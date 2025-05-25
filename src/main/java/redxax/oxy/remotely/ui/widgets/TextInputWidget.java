package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.util.Notification;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import static redxax.oxy.remotely.config.Config.*;

public class TextInputWidget extends AnimatedWidget {
    private String textValue = "Type...";
    private String placeholder = "";
    private int cursorPos = 0;
    private int selectionStart = 0;
    private int selectionEnd = 0;
    private float scrollOffset = 0f;
    private Runnable onChange;
    private int maxLength = 143;

    public static class Builder extends AnimatedWidget.Builder<TextInputWidget, Builder> {
        public Builder() { super(new TextInputWidget(0, 0, 150, 20)); }
        public Builder text(String t) {
            widget.textValue = t;
            widget.cursorPos = t.length();
            widget.selectionStart = t.length();
            widget.selectionEnd = t.length();
            return this;
        }
        public Builder placeholder(String p) { widget.placeholder = p; return this; }
        public Builder maxLength(int len) { widget.maxLength = len; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        @Override protected Builder self() { return this; }
    }

    public TextInputWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
    }

    public String getText() { return textValue; }

    public void setText(String text) {
        this.textValue = text.length() <= maxLength ? text : text.substring(0, maxLength);
        this.cursorPos = Math.min(cursorPos, this.textValue.length());
        this.selectionStart = Math.min(selectionStart, this.textValue.length());
        this.selectionEnd = Math.min(selectionEnd, this.textValue.length());
        updateScrollOffset();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2 + 1;

        updateScrollOffset();

        ctx.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());

        if (textValue.isEmpty()) {
            ctx.drawText(tr, Text.literal(placeholder), getX() + 5, textY, globalDarkTextColor, shadow);
        } else {
            if (isFocused() && selectionStart != selectionEnd) {
                int selStart = Math.min(selectionStart, selectionEnd);
                int selEnd = Math.max(selectionStart, selectionEnd);
                String textBeforeSel = selStart <= textValue.length() ? textValue.substring(0, selStart) : textValue;
                int selStartX = getX() + 5 + tr.getWidth(textBeforeSel) - (int)scrollOffset;
                String selectedText = selEnd <= textValue.length() ? textValue.substring(selStart, selEnd) : "";
                int selWidth = tr.getWidth(selectedText);
                ctx.fill(selStartX, textY, selStartX + selWidth, textY + tr.fontHeight, globalSelectionColor);
            }
            ctx.drawText(tr, Text.literal(textValue), getX() + 5 - (int)scrollOffset, textY, textColor, shadow);
        }
        if (isFocused()) {
            String beforeCursor = cursorPos <= textValue.length() ? textValue.substring(0, cursorPos) : textValue;
            int cursorPosX = getX() + 5 + tr.getWidth(beforeCursor) - (int)scrollOffset;
            ctx.fill(cursorPosX, textY, cursorPosX + 1, textY + tr.fontHeight, globalCursorAnimatedColor);
        }

        ctx.disableScissor();
    }

    private void updateScrollOffset() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int displayWidth = getWidth() - 10;
        int textWidth = tr.getWidth(textValue);
        String beforeCursor = cursorPos <= textValue.length() ? textValue.substring(0, cursorPos) : textValue;
        int cursorX = getX() + 5 + tr.getWidth(beforeCursor);
        if (textWidth > displayWidth) {
            if (cursorX > getX() + displayWidth) {
                scrollOffset = cursorX - (getX() + displayWidth);
            } else if (cursorX < getX() + 5) {
                scrollOffset = Math.max(0, scrollOffset - (getX() + 5 - cursorX));
            }
        } else {
            scrollOffset = 0;
        }
    }

    @Override
    protected void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            setFocused(true);
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            int relativeX = (int)(mouseX - getX() - 5 + scrollOffset);
            int newCursorPos = 0;
            int cumulativeWidth = 0;
            for (int i = 0; i <= textValue.length(); i++) {
                if (i < textValue.length()) {
                    int charWidth = tr.getWidth(textValue.substring(i, i + 1));
                    if (cumulativeWidth + charWidth / 2 > relativeX) {
                        newCursorPos = i;
                        break;
                    }
                    cumulativeWidth += charWidth;
                }
                newCursorPos = i;
            }
            cursorPos = MathHelper.clamp(newCursorPos, 0, textValue.length());
            selectionStart = cursorPos;
            selectionEnd = cursorPos;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isFocused() || button != 0) return false;
        int relativeX = (int)(mouseX - getX() - 5 + scrollOffset);
        int newCursorPos = 0;
        int cumulativeWidth = 0;
        for (int i = 0; i <= textValue.length(); i++) {
            if (i < textValue.length()) {
                int charWidth = tr.getWidth(textValue.substring(i, i + 1));
                if (cumulativeWidth + charWidth / 2 > relativeX) {
                    newCursorPos = i;
                    break;
                }
                cumulativeWidth += charWidth;
            }
            newCursorPos = i;
        }
        cursorPos = MathHelper.clamp(newCursorPos, 0, textValue.length());
        selectionEnd = cursorPos;

        if (onChange != null) {
            onChange.run();
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        System.out.println("Triggered keyPressed: " + keyCode + ", modifiers: " + modifiers);
        if (!isFocused()) return false;
        System.out.println("Passed");
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        if ((keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) && start < end) {
            textValue = textValue.substring(0, start) + textValue.substring(end);
            cursorPos = start;
            selectionStart = start;
            selectionEnd = start;
            if (onChange != null) onChange.run();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_BACKSPACE && cursorPos > 0) {
            int pos = cursorPos;
            while (pos > 0 && textValue.charAt(pos - 1) == ' ') pos--;
            while (pos > 0 && textValue.charAt(pos - 1) != ' ') pos--;
            textValue = textValue.substring(0, pos) + textValue.substring(cursorPos);
            cursorPos = pos;
            selectionStart = pos;
            selectionEnd = pos;
            if (onChange != null) onChange.run();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_DELETE && cursorPos < textValue.length()) {
            int pos = cursorPos;
            while (pos < textValue.length() && textValue.charAt(pos) == ' ') pos++;
            while (pos < textValue.length() && textValue.charAt(pos) != ' ') pos++;
            textValue = textValue.substring(0, cursorPos) + textValue.substring(pos);
            selectionStart = cursorPos;
            selectionEnd = cursorPos;
            if (onChange != null) onChange.run();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_LEFT && cursorPos > 0) {
            int pos = cursorPos;
            while (pos > 0 && textValue.charAt(pos - 1) == ' ') pos--;
            while (pos > 0 && textValue.charAt(pos - 1) != ' ') pos--;
            cursorPos = pos;
            if (shift) {
                selectionEnd = pos;
            } else {
                selectionStart = pos;
                selectionEnd = pos;
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_RIGHT && cursorPos < textValue.length()) {
            int pos = cursorPos;
            while (pos < textValue.length() && textValue.charAt(pos) != ' ') pos++;
            while (pos < textValue.length() && textValue.charAt(pos) == ' ') pos++;
            cursorPos = pos;
            if (shift) {
                selectionEnd = pos;
            } else {
                selectionStart = pos;
                selectionEnd = pos;
            }
            return true;
        }
        if (!ctrl && keyCode == GLFW.GLFW_KEY_LEFT && cursorPos > 0) {
            cursorPos--;
            if (shift) {
                selectionEnd = cursorPos;
            } else {
                selectionStart = cursorPos;
                selectionEnd = cursorPos;
            }
            return true;
        }
        if (!ctrl && keyCode == GLFW.GLFW_KEY_RIGHT && cursorPos < textValue.length()) {
            cursorPos++;
            if (shift) {
                selectionEnd = cursorPos;
            } else {
                selectionStart = cursorPos;
                selectionEnd = cursorPos;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            if (shift) {
                selectionEnd = 0;
            } else {
                selectionStart = 0;
                selectionEnd = 0;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            cursorPos = textValue.length();
            if (shift) {
                selectionEnd = textValue.length();
            } else {
                selectionStart = textValue.length();
                selectionEnd = textValue.length();
            }
            return true;
        }
        if (!ctrl && keyCode == GLFW.GLFW_KEY_DELETE && cursorPos < textValue.length()) {
            textValue = textValue.substring(0, cursorPos) + textValue.substring(cursorPos + 1);
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursorPos > 0) {
            textValue = textValue.substring(0, cursorPos - 1) + textValue.substring(cursorPos);
            cursorPos--;
            selectionStart = cursorPos;
            selectionEnd = cursorPos;
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && ctrl) {
            textValue = "";
            cursorPos = 0;
            selectionStart = 0;
            selectionEnd = 0;
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            setFocused(false);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectionStart = 0;
            selectionEnd = textValue.length();
            cursorPos = textValue.length();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            handleClipboardCopy();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            handleClipboardPaste();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused()) return false;

        int selStart = Math.min(selectionStart, selectionEnd);
        int selEnd = Math.max(selectionStart, selectionEnd);
        if (selStart != selEnd) {
            textValue = textValue.substring(0, selStart) + textValue.substring(selEnd);
            cursorPos = selStart;
            selectionStart = selStart;
            selectionEnd = selStart;
            if (onChange != null) onChange.run();
        }
        if (chr == 13 || chr == 27) return true;
        if (!Character.isISOControl(chr) && textValue.length() < maxLength) {
            textValue = textValue.substring(0, cursorPos) + chr + textValue.substring(cursorPos);
            cursorPos++;
            selectionStart = cursorPos;
            selectionEnd = cursorPos;
            if (onChange != null) onChange.run();
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    private void handleClipboardCopy() {
        try {
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            if (start < end) {
                String selectedText = textValue.substring(start, end);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selectedText), null);
            }
        } catch (Exception ignored) {}
    }

    private void handleClipboardPaste() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String clipboardText = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (clipboardText != null) {
                    int start = Math.min(selectionStart, selectionEnd);
                    int end = Math.max(selectionStart, selectionEnd);

                    int availableSpace = maxLength - (textValue.length() - (end - start));
                    if (availableSpace > 0) {
                        String textToPaste = clipboardText.length() <= availableSpace ? clipboardText : clipboardText.substring(0, availableSpace);
                        textValue = textValue.substring(0, start) + textToPaste + textValue.substring(end);
                        cursorPos = start + textToPaste.length();
                        selectionStart = cursorPos;
                        selectionEnd = cursorPos;
                        if (onChange != null) onChange.run();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        updateScrollOffset();
    }

    @Override
    public void setWidth(int w) {
        super.setWidth(w);
        updateScrollOffset();
    }
}