package redxax.oxy.explorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.CursorUtils;
import redxax.oxy.servers.ServerInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class FileEditorScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final Path filePath;
    private final ServerInfo serverInfo;
    private final ArrayList<String> fileContent = new ArrayList<>();
    private final MultiLineTextEditor textEditor;
    private final ArrayList<String> originalContent = new ArrayList<>();
    private boolean unsaved;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF555555;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private static long lastLeftClickTime = 0;
    private static int clickCount = 0;
    private int backButtonX;
    private int backButtonY;
    private int saveButtonX;
    private int saveButtonY;
    private int btnW;
    private int btnH;
    private static final double FAST_SCROLL_FACTOR = 3.0;
    private static final double HORIZONTAL_SCROLL_FACTOR = 10.0;

    public FileEditorScreen(MinecraftClient mc, Screen parent, Path filePath, ServerInfo info) {
        super(Text.literal("File Editor"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.filePath = filePath;
        this.serverInfo = info;
        if (serverInfo.isRemote) {
            try {
                if (serverInfo.remoteSSHManager == null) {
                    serverInfo.remoteSSHManager = new redxax.oxy.SSHManager(serverInfo);
                    serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                    serverInfo.remoteSSHManager.connectSFTP();
                } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                    serverInfo.remoteSSHManager.connectSFTP();
                }
                String remotePath = filePath.toString().replace("\\", "/");
                String content = serverInfo.remoteSSHManager.readRemoteFile(remotePath);
                String[] lines = content.split("\\r?\\n");
                for (String line : lines) {
                    fileContent.add(line);
                }
            } catch (Exception e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("File load error (remote): " + e.getMessage() + "\n");
                }
            }
        } else {
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                fileContent.addAll(reader.lines().toList());
            } catch (IOException e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("File load error: " + e.getMessage() + "\n");
                }
            }
        }
        this.textEditor = new MultiLineTextEditor(minecraftClient, fileContent, filePath.getFileName().toString());
        this.unsaved = false;
    }

    @Override
    protected void init() {
        super.init();
        this.textEditor.init(5, 35, this.width - 10, this.height - 45);
        btnW = 50;
        btnH = 20;
        saveButtonX = this.width - 60;
        saveButtonY = 5;
        backButtonX = saveButtonX - (btnW + 10);
        backButtonY = saveButtonY;
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        boolean used = textEditor.charTyped(chr, keyCode);
        if (used) {
            unsaved = true;
        }
        return used || super.charTyped(chr, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == this.minecraftClient.options.backKey.getDefaultKey().getCode() && keyCode != GLFW.GLFW_KEY_S) {
            minecraftClient.setScreen(parent);
            return true;
        }
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Z) {
            textEditor.undo();
            unsaved = true;
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Y) {
            textEditor.redo();
            unsaved = true;
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_A) {
            textEditor.selectAll();
            return true;
        }
        boolean used = textEditor.keyPressed(keyCode, modifiers);
        if (used) {
            unsaved = true;
        }
        return used || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            minecraftClient.setScreen(parent);
            return true;
        }
        boolean clickedText = textEditor.mouseClicked(mouseX, mouseY, button);
        if (clickedText) {
            return true;
        }
        if (mouseX >= saveButtonX && mouseX <= saveButtonX + btnW && mouseY >= saveButtonY && mouseY <= saveButtonY + btnH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            saveFile();
            return true;
        }
        if (mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            minecraftClient.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (textEditor.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (textEditor.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        long windowHandle = minecraftClient.getWindow().getHandle();
        boolean shiftHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrlHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (shiftHeld) {
            textEditor.scrollHoriz((int) (-vertAmount) * (int) HORIZONTAL_SCROLL_FACTOR);
        } else if (ctrlHeld) {
            textEditor.scrollVert((int) (-vertAmount) * (int) FAST_SCROLL_FACTOR);
        } else {
            textEditor.scrollVert((int) (-vertAmount));
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 30, lighterColor);
        drawInnerBorder(context, 0, 1, this.width, 30, borderColor);
        String titleText = "Editing: " + filePath.getFileName().toString() + (unsaved ? " *" : "");
        context.drawText(this.textRenderer, Text.literal(titleText), 10, 10, textColor, true);

        textEditor.render(context, mouseX, mouseY, delta);
        drawInnerBorder(context, 5, 35, this.width - 10, this.height - 45, borderColor);

        boolean hoveredSave = mouseX >= saveButtonX && mouseX <= saveButtonX + btnW && mouseY >= saveButtonY && mouseY <= saveButtonY + btnH;
        int bgSave = hoveredSave ? highlightColor : lighterColor;
        context.fill(saveButtonX, saveButtonY, saveButtonX + btnW, saveButtonY + btnH, bgSave);
        drawInnerBorder(context, saveButtonX, saveButtonY, btnW, btnH, borderColor);
        int tw = minecraftClient.textRenderer.getWidth("Save");
        int tx = saveButtonX + (btnW - tw) / 2;
        int ty = saveButtonY + (btnH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal("Save"), tx, ty, textColor, true);

        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor);
        int twb = minecraftClient.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal("Back"), txb, ty, textColor, true);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void saveFile() {
        ArrayList<String> newContent = new ArrayList<>(textEditor.getLines());
        if (serverInfo.isRemote) {
            try {
                if (serverInfo.remoteSSHManager == null) {
                    serverInfo.remoteSSHManager = new redxax.oxy.SSHManager(serverInfo);
                    serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                    serverInfo.remoteSSHManager.connectSFTP();
                } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                    serverInfo.remoteSSHManager.connectSFTP();
                }
                String remotePath = filePath.toString().replace("\\", "/");
                String joined = String.join("\n", newContent);
                serverInfo.remoteSSHManager.writeRemoteFile(remotePath, joined);
            } catch (Exception e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("File save error (remote): " + e.getMessage() + "\n");
                }
            }
        } else {
            try {
                Files.write(filePath, newContent);
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("File saved: " + filePath + "\n");
                }
            } catch (IOException e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("File save error: " + e.getMessage() + "\n");
                }
            }
        }
        unsaved = false;
    }

    private static class MultiLineTextEditor {
        private final MinecraftClient mc;
        private final ArrayList<String> lines;
        private final String fileName;
        private int x;
        private int y;
        private int width;
        private int height;
        private double smoothScrollOffsetVert = 0;
        private double smoothScrollOffsetHoriz = 0;
        private double targetScrollOffsetVert = 0;
        private double targetScrollOffsetHoriz = 0;
        private double scrollSpeed = 0.3;
        private int cursorLine;
        private int cursorPos;
        private int selectionStartLine = -1;
        private int selectionStartChar = -1;
        private int selectionEndLine = -1;
        private int selectionEndChar = -1;
        private final ArrayDeque<EditorState> undoStack = new ArrayDeque<>();
        private final ArrayDeque<EditorState> redoStack = new ArrayDeque<>();
        private int textPadding = 4;
        private int paddingTop = 5;
        private int paddingRight = 5;
        private int cursor = 0xFFFF0000;
        private float cursorOpacity = 1.0f;
        private boolean cursorFadingOut = true;
        private long lastCursorBlinkTime = 0;
        private static final long CURSOR_BLINK_INTERVAL = 30;

        public MultiLineTextEditor(MinecraftClient mc, ArrayList<String> content, String fileName) {
            this.mc = mc;
            this.lines = new ArrayList<>(content);
            this.fileName = fileName;
        }

        public void init(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.smoothScrollOffsetVert = 0;
            this.smoothScrollOffsetHoriz = 0;
            this.targetScrollOffsetVert = 0;
            this.targetScrollOffsetHoriz = 0;
            this.cursorLine = 0;
            this.cursorPos = 0;
            pushState();
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            smoothScrollOffsetVert += (targetScrollOffsetVert - smoothScrollOffsetVert) * scrollSpeed;
            smoothScrollOffsetHoriz += (targetScrollOffsetHoriz - smoothScrollOffsetHoriz) * scrollSpeed;

            context.enableScissor(x, y, x + width, y + height);

            int lineHeight = mc.textRenderer.fontHeight + 2;
            int visibleLines = height / lineHeight;

            for (int i = 0; i < visibleLines; i++) {
                int lineIndex = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + i;
                if (lineIndex < 0 || lineIndex >= lines.size()) continue;
                int renderY = y + i * lineHeight - (int) smoothScrollOffsetVert % lineHeight;
                String text = lines.get(lineIndex);
                Text syntaxColoredLine = SyntaxHighlighter.highlight(text, fileName);
                context.drawText(mc.textRenderer, syntaxColoredLine, x + textPadding - (int) smoothScrollOffsetHoriz, renderY, 0xFFFFFF, true);
                if (isLineSelected(lineIndex)) {
                    drawSelection(context, lineIndex, renderY, text, textPadding);
                }
                if (lineIndex == cursorLine && !hasSelection()) {
                    CursorUtils.updateCursorOpacity();
                    int cursorX = mc.textRenderer.getWidth(text.substring(0, Math.min(cursorPos, text.length())));
                    int cy = renderY;
                    int cursorColor = CursorUtils.blendColor();
                    context.fill(x + textPadding - (int) smoothScrollOffsetHoriz + cursorX, cy, x + textPadding - (int) smoothScrollOffsetHoriz + cursorX + 1, cy + lineHeight - 2, cursorColor);
                }
            }
            context.disableScissor();
        }

        private int getMaxLineWidth() {
            int maxWidth = 0;
            for (String line : lines) {
                int lineWidth = mc.textRenderer.getWidth(line);
                if (lineWidth > maxWidth) {
                    maxWidth = lineWidth;
                }
            }
            return maxWidth;
        }

        private boolean charTyped(char chr, int keyCode) {
            if (chr == '\n' || chr == '\r') {
                deleteSelection();
                pushState();
                if (cursorLine >= 0 && cursorLine < lines.size()) {
                    String oldLine = lines.get(cursorLine);
                    String before = oldLine.substring(0, Math.min(cursorPos, oldLine.length()));
                    String after = oldLine.substring(Math.min(cursorPos, oldLine.length()));
                    lines.set(cursorLine, before);
                    lines.add(cursorLine + 1, after);
                    cursorLine++;
                    cursorPos = 0;
                } else {
                    lines.add("");
                    cursorLine = lines.size() - 1;
                    cursorPos = 0;
                }
                scrollToCursor();
                return true;
            } else if (chr >= 32 && chr != 127) {
                deleteSelection();
                pushState();
                if (cursorLine < 0) cursorLine = 0;
                if (cursorLine >= lines.size()) lines.add("");
                String line = lines.get(cursorLine);
                int pos = Math.min(cursorPos, line.length());
                String newLine = line.substring(0, pos) + chr + line.substring(pos);
                lines.set(cursorLine, newLine);
                cursorPos++;
                scrollToCursor();
                return true;
            }
            return false;
        }

        public boolean keyPressed(int keyCode, int modifiers) {
            boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (ctrlHeld) {
                        deleteWord();
                        pushState();
                        scrollToCursor();
                        return true;
                    }
                    if (hasSelection()) {
                        deleteSelection();
                        pushState();
                        scrollToCursor();
                        return true;
                    }
                    if (cursorLine < 0 || cursorLine >= lines.size()) return true;
                    pushState();
                    if (cursorPos > 0) {
                        String line = lines.get(cursorLine);
                        String newLine = line.substring(0, cursorPos - 1) + line.substring(cursorPos);
                        lines.set(cursorLine, newLine);
                        cursorPos--;
                    } else {
                        if (cursorLine > 0) {
                            int oldLen = lines.get(cursorLine - 1).length();
                            lines.set(cursorLine - 1, lines.get(cursorLine - 1) + lines.get(cursorLine));
                            lines.remove(cursorLine);
                            cursorLine--;
                            cursorPos = oldLen;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (ctrlHeld) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        int newPos = moveCursorLeftWord();
                        cursorPos = newPos;
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    } else {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        if (cursorPos > 0) {
                            cursorPos--;
                        } else {
                            if (cursorLine > 0) {
                                cursorLine--;
                                cursorPos = lines.get(cursorLine).length();
                            }
                        }
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (ctrlHeld) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        int newPos = moveCursorRightWord();
                        cursorPos = newPos;
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    } else {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        if (cursorPos < lines.get(cursorLine).length()) {
                            cursorPos++;
                        } else {
                            if (cursorLine < lines.size() - 1) {
                                cursorLine++;
                                cursorPos = 0;
                            }
                        }
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (cursorLine < lines.size() - 1) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        cursorLine++;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    if (cursorLine > 0) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        cursorLine--;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrlHeld) {
                        deleteSelection();
                        pushState();
                        String clipboard = mc.keyboard.getClipboard();
                        for (char c : clipboard.toCharArray()) {
                            if (c == '\n' || c == '\r') {
                                if (cursorLine < lines.size()) {
                                    String oldLine = lines.get(cursorLine);
                                    String before = oldLine.substring(0, Math.min(cursorPos, oldLine.length()));
                                    String after = oldLine.substring(Math.min(cursorPos, oldLine.length()));
                                    lines.set(cursorLine, before);
                                    lines.add(cursorLine + 1, after);
                                    cursorLine++;
                                    cursorPos = 0;
                                }
                            } else {
                                if (cursorLine < 0) cursorLine = 0;
                                if (cursorLine >= lines.size()) lines.add("");
                                String line = lines.get(cursorLine);
                                int pos = Math.min(cursorPos, line.length());
                                String newLine = line.substring(0, pos) + c + line.substring(pos);
                                lines.set(cursorLine, newLine);
                                cursorPos++;
                            }
                        }
                        scrollToCursor();
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_C -> {
                    if (ctrlHeld && hasSelection()) {
                        copySelectionToClipboard();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (ctrlHeld && hasSelection()) {
                        copySelectionToClipboard();
                        deleteSelection();
                        pushState();
                        scrollToCursor();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    deleteSelection();
                    pushState();
                    if (cursorLine < 0) {
                        cursorLine = 0;
                    }
                    if (cursorLine >= lines.size()) {
                        lines.add("");
                    } else {
                        String currentLine = lines.get(cursorLine);
                        String beforeCursor = currentLine.substring(0, cursorPos);
                        String afterCursor = currentLine.substring(cursorPos);
                        lines.set(cursorLine, beforeCursor);
                        lines.add(cursorLine + 1, afterCursor);
                    }
                    cursorLine++;
                    cursorPos = 0;
                    scrollToCursor();
                    return true;
                }
            }
            return false;
        }

        private int moveCursorLeftWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            String line = lines.get(cursorLine);
            int index = Math.min(cursorPos - 1, line.length() - 1);
            while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            while (index >= 0 && !Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            if (index < 0 && cursorLine > 0) {
                cursorLine--;
                cursorPos = lines.get(cursorLine).length();
                return cursorPos;
            }
            return Math.max(0, index + 1);
        }

        private int moveCursorRightWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            String line = lines.get(cursorLine);
            int index = cursorPos;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index >= line.length() && cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = 0;
                return cursorPos;
            }
            return index;
        }

        private void deleteWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return;
            String line = lines.get(cursorLine);
            int startPos = cursorPos;
            while (startPos > 0 && Character.isWhitespace(line.charAt(startPos - 1))) {
                startPos--;
            }
            while (startPos > 0 && !Character.isWhitespace(line.charAt(startPos - 1))) {
                startPos--;
            }
            String newLine = line.substring(0, startPos) + line.substring(cursorPos);
            lines.set(cursorLine, newLine);
            cursorPos = startPos;
            scrollToCursor();
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLeftClickTime < 250) {
                    clickCount++;
                } else {
                    clickCount = 1;
                }
                lastLeftClickTime = currentTime;

                clearSelection();
                int lineHeight = mc.textRenderer.fontHeight + 2;
                int localY = (int) mouseY - y;
                int clickedLine = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + (localY / lineHeight);
                if (clickedLine >= 0 && clickedLine < lines.size()) {
                    cursorLine = clickedLine;
                    int localX = (int) mouseX - (x + textPadding) + (int) smoothScrollOffsetHoriz;
                    String text = lines.get(cursorLine);
                    int cPos = 0;
                    int widthSum = 0;
                    for (char c : text.toCharArray()) {
                        int charWidth = mc.textRenderer.getWidth(String.valueOf(c));
                        if (widthSum + charWidth / 2 >= localX) break;
                        widthSum += charWidth;
                        cPos++;
                    }
                    cursorPos = cPos;

                    if (clickCount == 2) {
                        int wordStart = cursorPos;
                        int wordEnd = cursorPos;
                        while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1)) && !"=\"'".contains(String.valueOf(text.charAt(wordStart - 1)))) {
                            wordStart--;
                        }
                        while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd)) && !"=\"'".contains(String.valueOf(text.charAt(wordEnd)))) {
                            wordEnd++;
                        }
                        selectionStartLine = cursorLine;
                        selectionStartChar = wordStart;
                        selectionEndLine = cursorLine;
                        selectionEndChar = wordEnd;
                    } else if (clickCount >= 3) {
                        selectionStartLine = cursorLine;
                        selectionStartChar = 0;
                        selectionEndLine = cursorLine;
                        selectionEndChar = text.length();
                    } else {
                        selectionStartLine = cursorLine;
                        selectionStartChar = cursorPos;
                        selectionEndLine = cursorLine;
                        selectionEndChar = cursorPos;
                    }
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int lineHeight = mc.textRenderer.fontHeight + 2;
                int localY = (int) mouseY - y;
                int dragLine = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + (localY / lineHeight);
                if (dragLine < 0) dragLine = 0;
                if (dragLine >= lines.size()) dragLine = lines.size() - 1;
                int localX = (int) mouseX - (x + textPadding) + (int) smoothScrollOffsetHoriz;
                String text = lines.get(dragLine);
                int cPos = 0;
                int widthSum = 0;
                for (char c : text.toCharArray()) {
                    int charWidth = mc.textRenderer.getWidth(String.valueOf(c));
                    if (widthSum + charWidth / 2 >= localX) break;
                    widthSum += charWidth;
                    cPos++;
                }
                cursorLine = dragLine;
                cursorPos = cPos;
                selectionEndLine = dragLine;
                selectionEndChar = cPos;
                scrollToCursor();
                return true;
            }
            return false;
        }

        public void scrollVert(int amount) {
            targetScrollOffsetVert += amount * (mc.textRenderer.fontHeight + 2);
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScroll = Math.max(0, lines.size() * (mc.textRenderer.fontHeight + 2) - height);
            if (targetScrollOffsetVert > maxScroll) targetScrollOffsetVert = maxScroll;
        }

        public void scrollHoriz(int amount) {
            targetScrollOffsetHoriz += amount;
            int maxScroll = Math.max(0, getMaxLineWidth() - width);
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            if (targetScrollOffsetHoriz > maxScroll) targetScrollOffsetHoriz = maxScroll;
        }

        private void deleteSelection() {
            if (!hasSelection()) return;
            int startLine = selectionStartLine;
            int endLine = selectionEndLine;
            int startChar = selectionStartChar;
            int endChar = selectionEndChar;
            if (startLine > endLine || (startLine == endLine && startChar > endChar)) {
                int tmpLine = startLine; startLine = endLine; endLine = tmpLine;
                int tmpChar = startChar; startChar = endChar; endChar = tmpChar;
            }
            ArrayList<String> newLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (i < startLine || i > endLine) {
                    newLines.add(lines.get(i));
                } else if (i == startLine && i == endLine) {
                    String line = lines.get(i);
                    String newLine = line.substring(0, Math.max(0, startChar)) + line.substring(Math.min(endChar, line.length()));
                    newLines.add(newLine);
                    cursorLine = i;
                    cursorPos = Math.max(0, startChar);
                } else if (i == startLine) {
                    String line = lines.get(i);
                    String newLine = line.substring(0, Math.max(0, startChar));
                    newLines.add(newLine);
                } else if (i == endLine) {
                    String line = lines.get(i);
                    String old = newLines.remove(newLines.size() - 1);
                    String combined = old + line.substring(Math.min(endChar, line.length()));
                    newLines.add(combined);
                    cursorLine = startLine;
                    cursorPos = old.length();
                }
            }
            lines.clear();
            lines.addAll(newLines);
            clearSelection();
            scrollToCursor();
        }

        private boolean hasSelection() {
            return !(selectionStartLine == selectionEndLine && selectionStartChar == selectionEndChar)
                    && selectionStartLine != -1 && selectionEndLine != -1;
        }

        private boolean isLineSelected(int lineNumber) {
            if (selectionStartLine == -1 || selectionEndLine == -1) return false;
            int startLine = Math.min(selectionStartLine, selectionEndLine);
            int endLine = Math.max(selectionStartLine, selectionEndLine);
            return lineNumber >= startLine && lineNumber <= endLine;
        }

        private void drawSelection(DrawContext context, int lineNumber, int yPosition, String lineText, int padding) {
            int selectionStart = 0;
            int selectionEnd = lineText.length();
            if (lineNumber == selectionStartLine) {
                selectionStart = selectionStartChar;
            }
            if (lineNumber == selectionEndLine) {
                selectionEnd = selectionEndChar;
            }
            if (selectionStart > selectionEnd) {
                int temp = selectionStart;
                selectionStart = selectionEnd;
                selectionEnd = temp;
            }
            if (selectionStart >= lineText.length() || selectionEnd < 0) {
                return;
            }
            selectionStart = Math.max(0, selectionStart);
            selectionEnd = Math.min(lineText.length(), selectionEnd);
            String beforeSelection = lineText.substring(0, selectionStart);
            String selectionText = lineText.substring(selectionStart, selectionEnd);
            int selectionXStart = x + padding + mc.textRenderer.getWidth(beforeSelection) - (int) smoothScrollOffsetHoriz;
            int selectionWidth = mc.textRenderer.getWidth(selectionText);
            int lineHeight = mc.textRenderer.fontHeight + 2;
            context.fill(selectionXStart, yPosition, selectionXStart + selectionWidth, yPosition + lineHeight - 2, 0x804A90E2);
        }

        public void copySelectionToClipboard() {
            String selectedText = getSelectedText();
            if (!selectedText.isEmpty()) {
                mc.keyboard.setClipboard(selectedText);
            }
            clearSelection();
        }

        private String getSelectedText() {
            if (selectionStartLine == -1 || selectionEndLine == -1) {
                return "";
            }
            int startLine = selectionStartLine;
            int endLine = selectionEndLine;
            int startChar = selectionStartChar;
            int endChar = selectionEndChar;
            if (startLine > endLine || (startLine == endLine && startChar > endChar)) {
                int tmpLine = startLine; startLine = endLine; endLine = tmpLine;
                int tmpChar = startChar; startChar = endChar; endChar = tmpChar;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = startLine; i <= endLine; i++) {
                if (i < 0 || i >= lines.size()) continue;
                String lineText = lines.get(i);
                int lineStartChar = (i == startLine) ? startChar : 0;
                int lineEndChar = (i == endLine) ? endChar : lineText.length();
                if (lineStartChar > lineEndChar) {
                    int temp = lineStartChar;
                    lineStartChar = lineEndChar;
                    lineEndChar = temp;
                }
                if (lineStartChar >= lineText.length() || lineEndChar < 0) {
                    continue;
                }
                lineStartChar = Math.max(0, lineStartChar);
                lineEndChar = Math.min(lineText.length(), lineEndChar);
                sb.append(lineText, lineStartChar, lineEndChar);
                if (i != endLine) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        private void clearSelection() {
            selectionStartLine = -1;
            selectionStartChar = -1;
            selectionEndLine = -1;
            selectionEndChar = -1;
        }

        public List<String> getLines() {
            return lines;
        }

        public void undo() {
            if (undoStack.size() > 1) {
                redoStack.push(currentState());
                undoStack.pop();
                EditorState state = undoStack.peek();
                lines.clear();
                lines.addAll(state.lines);
                cursorLine = state.cursorLine;
                cursorPos = state.cursorPos;
                clearSelection();
                scrollToCursor();
            }
        }

        public void redo() {
            if (!redoStack.isEmpty()) {
                undoStack.push(currentState());
                EditorState state = redoStack.pop();
                lines.clear();
                lines.addAll(state.lines);
                cursorLine = state.cursorLine;
                cursorPos = state.cursorPos;
                clearSelection();
                scrollToCursor();
            }
        }

        public void selectAll() {
            selectionStartLine = 0;
            selectionStartChar = 0;
            selectionEndLine = lines.size() - 1;
            selectionEndChar = lines.get(lines.size() - 1).length();
            cursorLine = lines.size() - 1;
            cursorPos = lines.get(lines.size() - 1).length();
            scrollToCursor();
        }

        private void pushState() {
            redoStack.clear();
            undoStack.push(currentState());
        }

        private EditorState currentState() {
            return new EditorState(new ArrayList<>(lines), cursorLine, cursorPos);
        }

        private void scrollToCursor() {
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int cursorY = cursorLine * lineHeight;
            double desiredScrollVert = cursorY - (height / 2.0) + (3 * lineHeight) - paddingTop;
            targetScrollOffsetVert = desiredScrollVert;
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScrollVert = Math.max(0, lines.size() * lineHeight - height);
            if (targetScrollOffsetVert > maxScrollVert) targetScrollOffsetVert = maxScrollVert;

            String lineText = lines.get(cursorLine);
            int cursorX = mc.textRenderer.getWidth(lineText.substring(0, Math.min(cursorPos, lineText.length())));
            double desiredScrollHoriz = cursorX - (width / 2.0) + (3 * mc.textRenderer.getWidth("word"));
            targetScrollOffsetHoriz = desiredScrollHoriz;
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            int maxScrollHoriz = Math.max(0, getMaxLineWidth() - width);
            if (targetScrollOffsetHoriz > maxScrollHoriz) targetScrollOffsetHoriz = maxScrollHoriz;
        }

        private static class EditorState {
            private final ArrayList<String> lines;
            private final int cursorLine;
            private final int cursorPos;

            public EditorState(ArrayList<String> lines, int cursorLine, int cursorPos) {
                this.lines = lines;
                this.cursorLine = cursorLine;
                this.cursorPos = cursorPos;
            }
        }
    }
}
