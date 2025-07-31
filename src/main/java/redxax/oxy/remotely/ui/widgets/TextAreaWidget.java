package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
    private int maxLength = 10000;
    private static final int LINE_SPACING = 2;
    private static final int PADDING = 5;

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

        @Override
        protected Builder self() {
            return this;
        }
    }

    public TextAreaWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
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
        if (onChange != null) {
            onChange.accept(getText());
        }
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

                if (isFocused() && selectionStartLine != selectionEndLine || selectionStartCol != selectionEndCol) {
                    drawSelection(ctx, i, lineY);
                }
                ctx.drawText(tr, lineText, getX() + PADDING - (int) scrollOffsetX, lineY, textColor, shadow);
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
        targetScrollOffsetY = (float) MathHelper.clamp(targetScrollOffsetY, 0, maxScrollY);

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
        targetScrollOffsetX = (float) MathHelper.clamp(targetScrollOffsetX, 0, maxScrollX);
    }

    private void clampScroll() {
        int lineH = tr.fontHeight + LINE_SPACING;
        int contentHeight = getHeight() - PADDING * 2;
        int contentWidth = getWidth() - PADDING * 2;

        int maxScrollY = Math.max(0, lines.size() * lineH - contentHeight);
        targetScrollOffsetY = (float) MathHelper.clamp(targetScrollOffsetY, 0, maxScrollY);
        scrollOffsetY = (float) MathHelper.clamp(scrollOffsetY, 0, maxScrollY);

        int maxScrollX = 0;
        for (StringBuilder line : lines) {
            maxScrollX = Math.max(maxScrollX, tr.getWidth(line.toString()));
        }
        maxScrollX = Math.max(0, maxScrollX - contentWidth);
        targetScrollOffsetX = (float) MathHelper.clamp(targetScrollOffsetX, 0, maxScrollX);
        scrollOffsetX = (float) MathHelper.clamp(scrollOffsetX, 0, maxScrollX);
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
            SelectionPoint p = getPosFromCoords(mouseX, mouseY);
            setCursor(p.line, p.col);
        }
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
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (handleSelectionKeys(keyCode, ctrl, shift)) return true;
        if (handleEditingKeys(keyCode, ctrl)) return true;

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleSelectionKeys(int keyCode, boolean ctrl, boolean shift) {
        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                selectionStartLine = 0;
                selectionStartCol = 0;
                cursorLine = selectionEndLine = lines.size() - 1;
                cursorCol = selectionEndCol = lines.get(cursorLine).length();
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
            if (cursorCol > 0) {
                if (shift) setCursorWithSelection(cursorLine, cursorCol - 1);
                else setCursor(cursorLine, cursorCol - 1);
            } else if (cursorLine > 0) {
                if (shift) setCursorWithSelection(cursorLine - 1, lines.get(cursorLine - 1).length());
                else setCursor(cursorLine - 1, lines.get(cursorLine - 1).length());
            }
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (cursorCol < lines.get(cursorLine).length()) {
                if (shift) setCursorWithSelection(cursorLine, cursorCol + 1);
                else setCursor(cursorLine, cursorCol + 1);
            } else if (cursorLine < lines.size() - 1) {
                if (shift) setCursorWithSelection(cursorLine + 1, 0);
                else setCursor(cursorLine + 1, 0);
            }
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

    private boolean handleEditingKeys(int keyCode, boolean ctrl) {
        if (hasSelection()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                deleteSelection();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                copySelection();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
                copySelection();
                deleteSelection();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            insertText("\n");
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorCol > 0) {
                lines.get(cursorLine).deleteCharAt(cursorCol - 1);
                setCursor(cursorLine, cursorCol - 1);
            } else if (cursorLine > 0) {
                int prevLineLen = lines.get(cursorLine - 1).length();
                lines.get(cursorLine - 1).append(lines.get(cursorLine));
                lines.remove(cursorLine);
                setCursor(cursorLine - 1, prevLineLen);
            }
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorCol < lines.get(cursorLine).length()) {
                lines.get(cursorLine).deleteCharAt(cursorCol);
                setCursor(cursorLine, cursorCol);
            } else if (cursorLine < lines.size() - 1) {
                lines.get(cursorLine).append(lines.get(cursorLine + 1));
                lines.remove(cursorLine + 1);
                setCursor(cursorLine, cursorCol);
            }
        } else if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            paste();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || Character.isISOControl(chr)) return false;
        deleteSelection();
        insertText(String.valueOf(chr));
        return true;
    }

    private void insertText(String text) {
        if (getText().length() + text.length() > maxLength) return;

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
        GLFW.glfwSetClipboardString(GLFW.glfwGetCurrentContext(), sb.toString());
    }

    private void paste() {
        deleteSelection();
        String clipboard = GLFW.glfwGetClipboardString(GLFW.glfwGetCurrentContext());
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