package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.explorer.SyntaxHighlighter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static net.minecraft.client.gui.screen.Screen.hasControlDown;
import static redxax.oxy.remotely.config.Config.*;

public class TextAreaWidget extends AnimatedWidget {

    private final List<StringBuilder> lines = new ArrayList<>(List.of(new StringBuilder()));
    private String placeholder = "Type...";
    private int cursorLine = 0;
    private int cursorCol = 0;
    private int selectionStartLine = 0;
    private int selectionStartCol = 0;
    private int selectionEndLine = 0;
    private int selectionEndCol = 0;
    private float scrollOffsetX = 0f;
    private float scrollOffsetY = 0f;
    private float targetScrollOffsetX = 0f;
    private float targetScrollOffsetY = 0f;
    public Consumer<String> onChange;
    private int maxLength = 100000;
    private static final int LINE_SPACING = 2;
    private static final int PADDING = 1;

    private boolean syntaxHighlighting = false;
    private String fileNameForSyntax;
    private long lastClickTime = 0;
    private int clickCount = 0;
    private final Deque<HistoryState> undoStack = new ArrayDeque<>(50);
    private final Deque<HistoryState> redoStack = new ArrayDeque<>(50);

    private static class HistoryState {
        final String text;
        final int cursorLine, cursorCol, selStartLine, selStartCol, selEndLine, selEndCol;

        HistoryState(String text, int cL, int cC, int sSL, int sSC, int sEL, int sEC) {
            this.text = text; this.cursorLine = cL; this.cursorCol = cC;
            this.selStartLine = sSL; this.selStartCol = sSC; this.selEndLine = sEL; this.selEndCol = sEC;
        }
    }

    public static class Builder extends AnimatedWidget.Builder<TextAreaWidget, Builder> {
        public Builder() {
            super(new TextAreaWidget(0, 0, 200, 100));
        }

        public Builder text(String t) {
            widget.setText(t);
            return this;
        }

        public Builder placeholder(String p) {
            widget.placeholder = p;
            return this;
        }

        public Builder maxLength(int len) {
            widget.maxLength = len;
            return this;
        }

        public Builder onChange(Consumer<String> r) {
            widget.onChange = r;
            return this;
        }

        public Builder syntaxHighlighting(String fileName) {
            widget.syntaxHighlighting = true;
            widget.fileNameForSyntax = fileName;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    public TextAreaWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
        animateElevation = enableHoverColors = false;
    }

    public String getText() {
        return String.join("\n", lines);
    }

    public void setText(String text) {
        lines.clear();
        String[] textLines = text.split("\n", -1);
        for (String line : textLines) {
            lines.add(new StringBuilder(line));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        setCursor(0, 0);
        undoStack.clear();
        redoStack.clear();
        pushUndoState();
        if (onChange != null) {
            onChange.accept(getText());
        }
    }

    private void applyState(HistoryState state) {
        lines.clear();
        String[] textLines = state.text.split("\n", -1);
        for (String line : textLines) {
            lines.add(new StringBuilder(line));
        }
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = state.cursorLine;
        cursorCol = state.cursorCol;
        selectionStartLine = state.selStartLine;
        selectionStartCol = state.selStartCol;
        selectionEndLine = state.selEndLine;
        selectionEndCol = state.selEndCol;
        ensureCursorVisible();
        if (onChange != null) onChange.accept(getText());
    }

    private void pushUndoState() {
        redoStack.clear();
        HistoryState state = new HistoryState(getText(), cursorLine, cursorCol, selectionStartLine, selectionStartCol, selectionEndLine, selectionEndCol);
        if (!undoStack.isEmpty() && undoStack.peek().text.equals(state.text)) return;
        if (undoStack.size() >= 50) {
            undoStack.removeLast();
        }
        undoStack.push(state);
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        HistoryState currentState = new HistoryState(getText(), cursorLine, cursorCol, selectionStartLine, selectionStartCol, selectionEndLine, selectionEndCol);
        redoStack.push(currentState);
        applyState(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        HistoryState currentState = new HistoryState(getText(), cursorLine, cursorCol, selectionStartLine, selectionStartCol, selectionEndLine, selectionEndCol);
        undoStack.push(currentState);
        applyState(redoStack.pop());
    }


    @Override
    public void tick() {
        super.tick();
        scrollOffsetX += (targetScrollOffsetX - scrollOffsetX) * globalScrollSpeed * deltaTime;
        scrollOffsetY += (targetScrollOffsetY - scrollOffsetY) * globalScrollSpeed * deltaTime;

        if (Math.abs(targetScrollOffsetX - scrollOffsetX) < 0.5f) scrollOffsetX = targetScrollOffsetX;
        if (Math.abs(targetScrollOffsetY - scrollOffsetY) < 0.5f) scrollOffsetY = targetScrollOffsetY;

        clampScroll();
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        int contentHeight = getHeight() - PADDING * 2;
        int lineH = tr.fontHeight + LINE_SPACING;

        ctx.enableScissor(getX() + PADDING, getY() + PADDING, getX() + getWidth() - PADDING, getY() + getHeight() - PADDING);

        if (getText().isEmpty() && !isFocused()) {
            ctx.drawText(tr, placeholder, getX() + PADDING, getY() + PADDING, globalDarkTextColor, shadow);
        } else {

            int startLine = (int) (scrollOffsetY / lineH);
            int endLine = Math.min(lines.size(), startLine + (contentHeight / lineH) + 2);

            for (int i = startLine; i < endLine; i++) {
                int lineY = getY() + PADDING + i * lineH - (int) scrollOffsetY;
                String lineText = lines.get(i).toString();
                Text displayLine;

                if (syntaxHighlighting && fileNameForSyntax != null) {
                    displayLine = SyntaxHighlighter.highlight(lineText, fileNameForSyntax);
                } else {
                    displayLine = Text.literal(lineText).styled(s -> s.withColor(textColor));
                }

                if (isFocused() && hasSelection()) {
                    drawSelection(ctx, i, lineY);
                }
                ctx.drawText(tr, displayLine, getX() + PADDING - (int) scrollOffsetX, lineY, globalTextColor, shadow);
            }

            if (isFocused() && System.currentTimeMillis() % 1000 > 500) {
                String beforeCursor = lines.get(cursorLine).substring(0, cursorCol);
                int cursorX = getX() + PADDING + tr.getWidth(beforeCursor) - (int) scrollOffsetX;
                int cursorY = getY() + PADDING + cursorLine * lineH - (int) scrollOffsetY;
                ctx.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + tr.fontHeight, globalCursorAnimatedColor);
            }
        }
        ctx.disableScissor();

        int totalContentHeight = lines.size() * lineH;
        if (totalContentHeight > contentHeight) {
            Render.ScrollBar.render(ctx, null, mouseX, mouseY, totalContentHeight, scrollOffsetY, getX() + getWidth() - 4, getY(), 2, getHeight());
        }
    }

    private void drawSelection(DrawContext ctx, int lineIndex, int lineY) {
        SelectionPoint start = getOrderedSelectionStart();
        SelectionPoint end = getOrderedSelectionEnd();

        if (lineIndex < start.line || lineIndex > end.line) return;

        String lineText = lines.get(lineIndex).toString();
        int selStartX, selEndX;

        if (lineIndex == start.line) {
            selStartX = tr.getWidth(lineText.substring(0, start.col));
        } else {
            selStartX = 0;
        }

        if (lineIndex == end.line) {
            selEndX = tr.getWidth(lineText.substring(0, end.col));
        } else {
            selEndX = tr.getWidth(lineText);
        }

        int x1 = getX() + PADDING - (int) scrollOffsetX + selStartX;
        int x2 = getX() + PADDING - (int) scrollOffsetX + selEndX;

        ctx.fill(x1, lineY - 1, x2, lineY + tr.fontHeight, globalSelectionColor);
    }

    public void setCursor(int line, int col) {
        cursorLine = Math.max(0, Math.min(lines.size() - 1, line));
        cursorCol = Math.max(0, Math.min(lines.get(cursorLine).length(), col));
        selectionStartLine = cursorLine;
        selectionStartCol = cursorCol;
        selectionEndLine = cursorLine;
        selectionEndCol = cursorCol;
        ensureCursorVisible();
    }

    private void setCursorWithSelection(int line, int col) {
        cursorLine = Math.max(0, Math.min(lines.size() - 1, line));
        cursorCol = Math.max(0, Math.min(lines.get(cursorLine).length(), col));
        selectionEndLine = cursorLine;
        selectionEndCol = cursorCol;
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        int lineH = tr.fontHeight + LINE_SPACING;
        int contentHeight = getHeight() - PADDING * 2;
        int contentWidth = getWidth() - PADDING * 2;

        int cursorY = cursorLine * lineH;
        if (cursorY < targetScrollOffsetY) {
            targetScrollOffsetY = cursorY;
        }
        if (cursorY + lineH > targetScrollOffsetY + contentHeight) {
            targetScrollOffsetY = cursorY + lineH - contentHeight;
        }

        int maxScrollY = Math.max(0, lines.size() * lineH - contentHeight);
        targetScrollOffsetY = MathHelper.clamp(targetScrollOffsetY, 0, maxScrollY);

        int cursorX = tr.getWidth(lines.get(cursorLine).substring(0, cursorCol));
        if (cursorX < targetScrollOffsetX) {
            targetScrollOffsetX = cursorX;
        }
        if (cursorX > targetScrollOffsetX + contentWidth) {
            targetScrollOffsetX = cursorX - contentWidth;
        }

        int maxScrollX = 0;
        for (StringBuilder line : lines) {
            maxScrollX = Math.max(maxScrollX, tr.getWidth(line.toString()));
        }
        maxScrollX = Math.max(0, maxScrollX - contentWidth);
        targetScrollOffsetX = MathHelper.clamp(targetScrollOffsetX, 0, maxScrollX);
    }

    private void clampScroll() {
        int lineH = tr.fontHeight + LINE_SPACING;
        int contentHeight = getHeight() - PADDING * 2;
        int contentWidth = getWidth() - PADDING * 2;

        int maxScrollY = Math.max(0, lines.size() * lineH - contentHeight);
        targetScrollOffsetY = MathHelper.clamp(targetScrollOffsetY, 0, maxScrollY);
        scrollOffsetY = MathHelper.clamp(scrollOffsetY, 0, maxScrollY);

        int maxScrollX = 0;
        for (StringBuilder line : lines) {
            maxScrollX = Math.max(maxScrollX, tr.getWidth(line.toString()));
        }
        maxScrollX = Math.max(0, maxScrollX - contentWidth);
        targetScrollOffsetX = MathHelper.clamp(targetScrollOffsetX, 0, maxScrollX);
        scrollOffsetX = MathHelper.clamp(scrollOffsetX, 0, maxScrollX);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            targetScrollOffsetY -= (float) (verticalAmount * (tr.fontHeight + LINE_SPACING));
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            long time = System.currentTimeMillis();
            if (time - lastClickTime < 500) {
                clickCount++;
            } else {
                clickCount = 1;
            }
            lastClickTime = time;

            SelectionPoint p = getPosFromCoords(mouseX, mouseY);
            if (clickCount == 2) {
                selectWordAt(p.line, p.col);
            } else if (clickCount == 3) {
                selectLineAt(p.line);
                clickCount = 0;
            } else {
                setCursor(p.line, p.col);
            }
        }
    }

    private void selectWordAt(int line, int col) {
        String lineText = lines.get(line).toString();
        if (lineText.isEmpty()) return;

        col = Math.min(col, lineText.length());

        int start = col;
        while(start > 0 && !Character.isWhitespace(lineText.charAt(start-1))) start--;

        int end = col;
        while(end < lineText.length() && !Character.isWhitespace(lineText.charAt(end))) end++;

        selectionStartLine = line;
        selectionStartCol = start;
        selectionEndLine = line;
        selectionEndCol = end;
        cursorLine = line;
        cursorCol = end;
        ensureCursorVisible();
    }

    private void selectLineAt(int line) {
        selectionStartLine = line;
        selectionStartCol = 0;
        selectionEndLine = line;
        selectionEndCol = lines.get(line).length();
        cursorLine = line;
        cursorCol = selectionEndCol;
        ensureCursorVisible();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isFocused() && button == 0) {
            SelectionPoint p = getPosFromCoords(mouseX, mouseY);
            setCursorWithSelection(p.line, p.col);
            return true;
        }
        return false;
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        boolean ctrl = hasControlDown();
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (handleSelectionKeys(keyCode, ctrl, shift)) return true;
        if (handleEditingKeys(keyCode, ctrl, shift)) return true;

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleSelectionKeys(int keyCode, boolean ctrl, boolean shift) {
        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                selectionStartLine = 0;
                selectionStartCol = 0;
                cursorLine = selectionEndLine = lines.size() - 1;
                cursorCol = selectionEndCol = lines.get(cursorLine).length();
                ensureCursorVisible();
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (cursorCol > 0) {
                    String lineText = lines.get(cursorLine).toString();
                    int newCol = cursorCol;
                    while (newCol > 0 && Character.isWhitespace(lineText.charAt(newCol - 1))) newCol--;
                    while (newCol > 0 && !Character.isWhitespace(lineText.charAt(newCol - 1))) newCol--;
                    if (shift) setCursorWithSelection(cursorLine, newCol); else setCursor(cursorLine, newCol);
                } else if (cursorLine > 0) {
                    if (shift) setCursorWithSelection(cursorLine - 1, lines.get(cursorLine - 1).length());
                    else setCursor(cursorLine - 1, lines.get(cursorLine - 1).length());
                }
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (cursorCol < lines.get(cursorLine).length()) {
                    String lineText = lines.get(cursorLine).toString();
                    int newCol = cursorCol;
                    while (newCol < lineText.length() && !Character.isWhitespace(lineText.charAt(newCol))) newCol++;
                    while (newCol < lineText.length() && Character.isWhitespace(lineText.charAt(newCol))) newCol++;
                    if (shift) setCursorWithSelection(cursorLine, newCol); else setCursor(cursorLine, newCol);
                } else if (cursorLine < lines.size() - 1) {
                    if (shift) setCursorWithSelection(cursorLine + 1, 0); else setCursor(cursorLine + 1, 0);
                }
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                if (shift) setCursorWithSelection(0, 0); else setCursor(0, 0);
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                int lastLine = lines.size() - 1;
                int lastCol = lines.get(lastLine).length();
                if (shift) setCursorWithSelection(lastLine, lastCol); else setCursor(lastLine, lastCol);
            } else {
                return false;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if(shift) setCursorWithSelection(cursorLine, cursorCol-1); else if (hasSelection()) setCursor(getOrderedSelectionStart().line, getOrderedSelectionStart().col); else setCursor(cursorLine, cursorCol-1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if(shift) setCursorWithSelection(cursorLine, cursorCol+1); else if (hasSelection()) setCursor(getOrderedSelectionEnd().line, getOrderedSelectionEnd().col); else setCursor(cursorLine, cursorCol+1);
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            if (cursorLine > 0) {
                if (shift) setCursorWithSelection(cursorLine - 1, cursorCol);
                else setCursor(cursorLine - 1, cursorCol);
            }
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (cursorLine < lines.size() - 1) {
                if (shift) setCursorWithSelection(cursorLine + 1, cursorCol);
                else setCursor(cursorLine + 1, cursorCol);
            }
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            if (shift) setCursorWithSelection(cursorLine, 0);
            else setCursor(cursorLine, 0);
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            if (shift) setCursorWithSelection(cursorLine, lines.get(cursorLine).length());
            else setCursor(cursorLine, lines.get(cursorLine).length());
        } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            int lineH = tr.fontHeight + LINE_SPACING;
            int contentHeight = getHeight() - PADDING * 2;
            int pageAmount = Math.max(1, contentHeight / lineH);
            int newLine = Math.max(0, cursorLine - pageAmount);
            if (shift) setCursorWithSelection(newLine, cursorCol); else setCursor(newLine, cursorCol);
        } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int lineH = tr.fontHeight + LINE_SPACING;
            int contentHeight = getHeight() - PADDING * 2;
            int pageAmount = Math.max(1, contentHeight / lineH);
            int newLine = Math.min(lines.size() - 1, cursorLine + pageAmount);
            if (shift) setCursorWithSelection(newLine, cursorCol); else setCursor(newLine, cursorCol);
        } else {
            return false;
        }
        return true;
    }

    private boolean handleEditingKeys(int keyCode, boolean ctrl, boolean shift) {
        if(ctrl) {
            if(keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
            if(keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
        }

        if (hasSelection()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                pushUndoState(); deleteSelection();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) { copySelection(); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
                pushUndoState(); copySelection(); deleteSelection();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            pushUndoState(); insertText("\n");
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            pushUndoState();
            if (cursorCol > 0) {
                lines.get(cursorLine).deleteCharAt(cursorCol - 1);
                setCursor(cursorLine, cursorCol - 1);
            } else if (cursorLine > 0) {
                int prevLineLen = lines.get(cursorLine - 1).length();
                lines.get(cursorLine - 1).append(lines.get(cursorLine));
                lines.remove(cursorLine);
                setCursor(cursorLine - 1, prevLineLen);
            }
            if(onChange != null) onChange.accept(getText());
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            pushUndoState();
            if (cursorCol < lines.get(cursorLine).length()) {
                lines.get(cursorLine).deleteCharAt(cursorCol);
                setCursor(cursorLine, cursorCol);
            } else if (cursorLine < lines.size() - 1) {
                lines.get(cursorLine).append(lines.get(cursorLine + 1));
                lines.remove(cursorLine + 1);
                setCursor(cursorLine, cursorCol);
            }
            if(onChange != null) onChange.accept(getText());
        } else if (keyCode == GLFW.GLFW_KEY_TAB) {
            handleTab(shift);
        }
        else if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            pushUndoState(); paste();
        } else {
            return false;
        }
        return true;
    }

    private void handleTab(boolean shift) {
        pushUndoState();
        if (shift) {
            if (hasSelection()) {
                SelectionPoint start = getOrderedSelectionStart();
                SelectionPoint end = getOrderedSelectionEnd();
                for (int i = start.line; i <= end.line; i++) {
                    unindentLine(i);
                }
            } else {
                unindentLine(cursorLine);
            }
        } else {
            if (hasSelection()) {
                SelectionPoint start = getOrderedSelectionStart();
                SelectionPoint end = getOrderedSelectionEnd();
                for (int i = start.line; i <= end.line; i++) {
                    indentLine(i);
                }
            } else {
                insertText("\t");
            }
        }
        if (onChange != null) onChange.accept(getText());
    }

    private void indentLine(int lineIndex) {
        lines.get(lineIndex).insert(0, "\t");
    }

    private void unindentLine(int lineIndex) {
        StringBuilder line = lines.get(lineIndex);
        if (!line.isEmpty() && line.charAt(0) == '\t') {
            line.deleteCharAt(0);
        } else if (!line.isEmpty() && line.charAt(0) == ' ') {
            int spaces = 0;
            for (int i = 0; i < Math.min(4, line.length()); i++) {
                if (line.charAt(i) == ' ') spaces++; else break;
            }
            line.delete(0, spaces);
        }
    }


    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || Character.isISOControl(chr)) return false;
        pushUndoState();
        deleteSelection();
        insertText(String.valueOf(chr));
        return true;
    }

    private void insertText(String text) {
        if (getText().length() + text.length() > maxLength) return;

        text = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] newLines = text.split("\n", -1);
        StringBuilder currentLine = lines.get(cursorLine);
        String restOfLine = currentLine.substring(cursorCol);
        currentLine.delete(cursorCol, currentLine.length());

        currentLine.append(newLines[0]);
        int lastLine = cursorLine;
        int lastCol = currentLine.length();

        for (int i = 1; i < newLines.length; i++) {
            lastLine++;
            lines.add(lastLine, new StringBuilder(newLines[i]));
        }

        lines.get(lastLine).append(restOfLine);
        setCursor(lastLine, lastCol);
        if (onChange != null) onChange.accept(getText());
    }

    private SelectionPoint getPosFromCoords(double mouseX, double mouseY) {
        int lineH = tr.fontHeight + LINE_SPACING;
        int relativeY = (int) (mouseY - (getY() + PADDING) + scrollOffsetY);
        int line = Math.max(0, Math.min(lines.size() - 1, relativeY / lineH));

        int relativeX = (int) (mouseX - (getX() + PADDING) + scrollOffsetX);
        String lineText = lines.get(line).toString();
        int col = 0;
        int minDx = Integer.MAX_VALUE;
        for (int i = 0; i <= lineText.length(); i++) {
            int charX = tr.getWidth(lineText.substring(0, i));
            int dx = Math.abs(relativeX - charX);
            if (dx < minDx) {
                minDx = dx;
                col = i;
            }
        }
        return new SelectionPoint(line, col);
    }

    private boolean hasSelection() {
        return selectionStartLine != selectionEndLine || selectionStartCol != selectionEndCol;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        SelectionPoint start = getOrderedSelectionStart();
        SelectionPoint end = getOrderedSelectionEnd();

        if (start.line == end.line) {
            lines.get(start.line).delete(start.col, end.col);
        } else {
            lines.get(start.line).delete(start.col, lines.get(start.line).length());
            lines.get(start.line).append(lines.get(end.line).substring(end.col));
            if (end.line >= start.line + 1) {
                lines.subList(start.line + 1, end.line + 1).clear();
            }
        }
        setCursor(start.line, start.col);
        if (onChange != null) onChange.accept(getText());
    }

    private void copySelection() {
        if (!hasSelection()) return;
        SelectionPoint start = getOrderedSelectionStart();
        SelectionPoint end = getOrderedSelectionEnd();

        StringBuilder sb = new StringBuilder();
        for (int i = start.line; i <= end.line; i++) {
            String line = lines.get(i).toString();
            int c_start = (i == start.line) ? start.col : 0;
            int c_end = (i == end.line) ? end.col : line.length();
            sb.append(line, c_start, c_end);
            if (i < end.line) {
                sb.append("\n");
            }
        }
        mc.keyboard.setClipboard(sb.toString());
    }

    private void paste() {
        deleteSelection();
        String clipboard = mc.keyboard.getClipboard();
        if (clipboard != null) {
            insertText(clipboard);
        }
    }

    private SelectionPoint getOrderedSelectionStart() {
        SelectionPoint p1 = new SelectionPoint(selectionStartLine, selectionStartCol);
        SelectionPoint p2 = new SelectionPoint(selectionEndLine, selectionEndCol);
        return p1.compareTo(p2) <= 0 ? p1 : p2;
    }

    private SelectionPoint getOrderedSelectionEnd() {
        SelectionPoint p1 = new SelectionPoint(selectionStartLine, selectionStartCol);
        SelectionPoint p2 = new SelectionPoint(selectionEndLine, selectionEndCol);
        return p1.compareTo(p2) > 0 ? p1 : p2;
    }

    private static class SelectionPoint implements Comparable<SelectionPoint> {
        int line, col;

        SelectionPoint(int line, int col) {
            this.line = line;
            this.col = col;
        }

        @Override
        public int compareTo(SelectionPoint o) {
            if (line != o.line) return Integer.compare(line, o.line);
            return Integer.compare(col, o.col);
        }
    }

    public void setPlaceholder(String s) {
        this.placeholder = s;
    }
}