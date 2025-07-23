package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.terminal.ServerTerminalInstance;
import redxax.oxy.remotely.terminal.TerminalInstance;
import net.minecraft.client.gui.DrawContext;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static redxax.oxy.remotely.RemotelyClient.os;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class TerminalWidget extends AnimatedWidget {

    private TerminalInstance terminalInstance;
    private final StringBuilder terminalOutput = new StringBuilder();
    private final List<LineText> wrappedLinesCache = new ArrayList<>();
    private int scrollOffset = 0;
    private static final Pattern TMUX_STATUS_PATTERN = Pattern.compile(".*\\d{1,2}:\\d{2}\\s\\d{2}-[A-Za-z]{3}-\\d{2}.*");
    private final Pattern BRACKET_KEYWORD_PATTERN = Pattern.compile("\\[(.*?)\\b(WARNING|WARN|ERROR|INFO)\\b(.*?)]");
    private static final Pattern IP_PATTERN = Pattern.compile("(?<![\\w:])((?:\\d{1,3}\\.){3}\\d{1,3})(?![\\w:])");
    private boolean isSelecting = false;
    private int selectionStartLine = -1;
    private int selectionStartChar = -1;
    private int selectionEndLine = -1;
    private int selectionEndChar = -1;
    private String tmuxStatusLine = "";
    private float currentScrollOffset = 0;
    public float targetScrollOffset = 0;
    private int lastTerminalWidth = 0;

    private boolean showStatusBar = true;
    private boolean showInputField = true;
    private boolean enableSelection = true;
    private String customLeftStatus = null;
    private String customRightStatus = null;
    public boolean isInputActive = true;
    private boolean isServerTerminal = false;

    static {
        System.setProperty("jline.ansi", "true");
        System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
        System.setProperty("jansi.passthrough", "true");
        System.setProperty("jansi.force", "true");
        System.setProperty("jansi.strip", "false");
        System.setProperty("jansi.disable", "false");
        System.setProperty("net.kyori.ansi.colorLevel", "indexed256");
    }

    public static class Builder extends AnimatedWidget.Builder<TerminalWidget, Builder> {
        public Builder(TerminalInstance terminalInstance) {
            super(new TerminalWidget(0, 0, 100, 100, terminalInstance));
        }

        public Builder terminalInstance(TerminalInstance instance) {
            widget.setTerminalInstance(instance);
            return self();
        }

        public Builder showStatusBar(boolean show) {
            widget.setShowStatusBar(show);
            return self();
        }

        public Builder showInputField(boolean show) {
            widget.setShowInputField(show);
            return self();
        }

        public Builder enableSelection(boolean enable) {
            widget.setEnableSelection(enable);
            return self();
        }

        public Builder isInputActive(boolean active) {
            widget.isInputActive = active;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    public TerminalWidget(int x, int y, int width, int height, TerminalInstance terminalInstance) {
        super(x, y, width, height, Text.literal(""));
        setTerminalInstance(terminalInstance);
        this.lastTerminalWidth = width;
    }

    public void setTerminalInstance(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
        if (this.terminalInstance instanceof ServerTerminalInstance) {
            this.isServerTerminal = true;
            this.accentType = AccentType.CALM;
        } else {
            this.isServerTerminal = false;
            this.accentType = AccentType.DEFAULT;
        }
        rewrap();
    }

    public void setShowStatusBar(boolean showStatusBar) { this.showStatusBar = showStatusBar; }
    public void setShowInputField(boolean showInputField) { this.showInputField = showInputField; }
    public void setEnableSelection(boolean enableSelection) { this.enableSelection = enableSelection; }

    public void setCustomStatus(String left, String right) {
        this.customLeftStatus = left;
        this.customRightStatus = right;
    }

    public void clearCustomStatus() {
        this.customLeftStatus = null;
        this.customRightStatus = null;
    }

    @Override
    public void tick() {
        super.tick();

        float deltaScroll = targetScrollOffset - currentScrollOffset;
        currentScrollOffset += deltaScroll * globalScrollSpeed * deltaTime;

        int maxScroll = Math.max(0, getTotalScrollHeight() - getTextAreaHeight());
        if (currentScrollOffset < 0) {
            currentScrollOffset += (-currentScrollOffset) * 0.3f;
        } else if (currentScrollOffset > maxScroll) {
            currentScrollOffset -= (currentScrollOffset - maxScroll) * 0.3f;
        }
        scrollOffset = (int) currentScrollOffset;

        if (getWidth() != lastTerminalWidth && getWidth() > 0) {
            stickToBottom(8);
            rewrap();
            lastTerminalWidth = getWidth();
        }
    }

    @Override
    protected void drawContent(DrawContext context, int mouseX, int mouseY) {
        int padding = 2;
        int textAreaX = getX() + padding;
        int textAreaY = getY() + padding;
        int textAreaWidth = getWidth() - (2 * padding);
        int textAreaHeight = getTextAreaHeight();

        context.enableScissor(textAreaX, textAreaY, textAreaX + textAreaWidth, textAreaY + textAreaHeight);

        int lineHeight = getLineHeight();
        int firstLine = (int) Math.floor(currentScrollOffset / lineHeight);
        int visibleLines = textAreaHeight / lineHeight + 3;

        for (int i = 0; i < visibleLines; i++) {
            int lineIndex = firstLine + i;
            if (lineIndex < 0 || lineIndex >= getTotalLines()) continue;

            int renderY = textAreaY + i * lineHeight - ((int) currentScrollOffset % lineHeight);
            LineText lineText;
            synchronized (wrappedLinesCache) {
                if (lineIndex >= wrappedLinesCache.size()) continue;
                lineText = wrappedLinesCache.get(lineIndex);
            }

            if (isLineSelected(lineIndex)) {
                LineInfo tempLineInfo = new LineInfo(lineIndex, renderY, mc.textRenderer.fontHeight, lineText.orderedText, lineText.plainText);
                drawSelection(context, tempLineInfo, textAreaX);
            }
            context.drawText(mc.textRenderer, lineText.orderedText, textAreaX, renderY, terminalTextColor, shadow);
        }
        context.disableScissor();

        if (showInputField) {
            drawInputField(context);
        }
        if (showStatusBar) {
            drawStatusBar(context);
        }
    }

    private void drawInputField(DrawContext context) {
        if (terminalInstance == null || terminalInstance.inputHandler == null) return;

        int padding = 2;
        int inputY = getY() + getHeight() - getStatusBarHeight() - getInputFieldHeight() - (showInputField ? 2:0);
        int inputX = getX() + padding;

        String inputPrompt = terminalInstance.getSSHManager().isAwaitingPassword() ? "Password: " : "> ";
        String inputText = inputPrompt + terminalInstance.inputHandler.getInputBuffer().toString();

        context.drawText(mc.textRenderer, Text.literal(inputText), inputX, inputY, terminalTextInputColor, shadow);

        if (isInputActive && isFocused()) {
            int cursorInputPosition = Math.min(terminalInstance.inputHandler.getCursorPosition(), terminalInstance.inputHandler.getInputBuffer().length());
            String beforeCursor = inputPrompt + terminalInstance.inputHandler.getInputBuffer().substring(0, cursorInputPosition);
            int cursorXPos = inputX + mc.textRenderer.getWidth(beforeCursor);
            int cursorHeight = mc.textRenderer.fontHeight;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 1000);
            context.fill(cursorXPos, inputY, cursorXPos + 1, inputY + cursorHeight, globalCursorAnimatedColor);
            context.getMatrices().pop();

            String suggestion = terminalInstance.inputHandler.getTabCompletionSuggestion();
            if (!suggestion.isEmpty() && !terminalInstance.inputHandler.getInputBuffer().isEmpty()) {
                int inputTextWidth = mc.textRenderer.getWidth(inputText);
                context.drawText(mc.textRenderer, Text.literal(suggestion).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(globalDarkTextColor))), inputX + inputTextWidth, inputY, globalDarkTextColor, shadow);
            }
        }
    }

    private void drawStatusBar(DrawContext context) {
        int statusBarY = getY() + getHeight() - getStatusBarHeight();
        context.fill(getX(), statusBarY, getX() + getWidth(), statusBarY + getStatusBarHeight(), terminalStatusBarColor);

        String[] statusTexts = getStatusBarStrings(getWidth() - 4);
        int rightWidth = mc.textRenderer.getWidth(statusTexts[1]);

        context.drawText(mc.textRenderer, Text.literal(statusTexts[0]), getX() + 2, statusBarY + (getStatusBarHeight() - mc.textRenderer.fontHeight) / 2, terminalTextColor, shadow);
        context.drawText(mc.textRenderer, Text.literal(statusTexts[1]), getX() + getWidth() - 2 - rightWidth, statusBarY + (getStatusBarHeight() - mc.textRenderer.fontHeight) / 2, terminalTextColor, shadow);
    }

    @Override
    protected void drawBackground(DrawContext ctx) {
        float alpha = getEntranceAlpha();
        int terminalBgColor = applyAlpha(backgroundColor, alpha);
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), terminalBgColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && isMouseOver(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && enableSelection && isMouseOverTerminal(mouseX, mouseY)) {
                isSelecting = true;
                updateSelectionStart(mouseX, mouseY);
                updateSelectionEnd(mouseX, mouseY);
            }
            onClick(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isSelecting) {
                isSelecting = false;
                if (enableSelection) {
                    updateSelectionEnd(mouseX, mouseY);
                }
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (enableSelection) {
                updateSelectionEnd(mouseX, mouseY);
                scrollToEdgesTerminal(mouseY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (active && visible && isMouseOverTerminal(mouseX, mouseY)) {
            scroll(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    public void appendOutput(String text) {
        if (terminalInstance == null) return;
        text = text.replace("\r", "").replace("\t", "    ");
        synchronized (terminalOutput) {
            terminalOutput.append(text);
        }
        rewrap();
        stickToBottom(3);
    }

    public void clearOutput() {
        synchronized (terminalOutput) {
            terminalOutput.setLength(0);
        }
        synchronized (wrappedLinesCache) {
            wrappedLinesCache.clear();
        }
        rewrap();
    }

    public void copySelectionToClipboard() {
        if (!enableSelection) return;
        String selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            mc.keyboard.setClipboard(selectedText);
        }
        selectionStartLine = -1;
        selectionStartChar = -1;
        selectionEndLine = -1;
        selectionEndChar = -1;
    }

    public String getTerminalContext() {
        if (terminalInstance == null) return "";
        StringBuilder lines = new StringBuilder();
        if (terminalInstance.getSSHManager() != null && terminalInstance.getSSHManager().isSSH()) {
            lines.append("The User Is Using a Remote SSH Server (Linux) \n");
        } else {
            lines.append("The User Operating System Is: ").append(os.contains("win") ? "Windows" : os.contains("mac") ? "MacOS" : os.contains("nix") || os.contains("nux") ? "Linux" : "Unknown OS").append("\n");
        }
        lines.append("This Is The Current Terminal Logs: \n");
        synchronized (wrappedLinesCache) {
            for (LineText lineText : wrappedLinesCache) {
                lines.append(lineText.plainText).append("\n");
            }
        }
        return lines.toString();
    }

    public void scroll(int direction) {
        int scrollMultiplier = InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) ? 5 : 1;
        float scrollAmount = getLineHeight() * scrollMultiplier;
        targetScrollOffset -= direction * scrollAmount;
        int maxScroll = Math.max(0, getTotalScrollHeight() - getTextAreaHeight());
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
    }

    public void scrollToTop() {
        targetScrollOffset = 0;
    }

    public void scrollToBottom() {
        targetScrollOffset = Math.max(0, getTotalScrollHeight() - getTextAreaHeight());
    }

    private void stickToBottom(int thresholdMultiplier) {
        int maxScroll = Math.max(0, getTotalScrollHeight() - getTextAreaHeight());
        int threshold = getLineHeight() * thresholdMultiplier;
        if (targetScrollOffset >= maxScroll - threshold) {
            scrollToBottom();
        }
    }

    public void rewrap() {
        if (terminalInstance == null) return;
        List<LineText> newWrappedLines = new ArrayList<>();
        String output;
        synchronized (terminalOutput) {
            output = terminalOutput.toString();
        }
        Pattern extraPattern = Pattern.compile("^(.*\\d{1,2}:\\d{2}\\s\\d{2}-[A-Za-z]{3}-)\\d{2}(.*)$");
        String[] lines = output.split("\n", -1);
        int wrapWidth = getWidth() - 10;
        if (wrapWidth <= 0) return;

        for (String rawLine : lines) {
            if (rawLine.isEmpty()) {
                newWrappedLines.add(new LineText(Text.literal("").asOrderedText(), ""));
                continue;
            }
            String line = obfuscateIps(rawLine.replace("\0", ""));
            String trimmed = line.trim();
            if (trimmed.equals(">")) continue;
            String plain = removeAllAnsiSequences(line);
            Matcher extraMatcher = extraPattern.matcher(plain);
            if (extraMatcher.matches()) {
                tmuxStatusLine = extraMatcher.group(1) + new SimpleDateFormat("yy").format(new Date());
                String extra = extraMatcher.group(2);
                if (!extra.isEmpty()) {
                    List<StyleTextPair> extraSegments = parseKeywordsAndHighlight(extra);
                    newWrappedLines.addAll(wrapStyledText(extraSegments, wrapWidth));
                }
                continue;
            } else if (TMUX_STATUS_PATTERN.matcher(plain).matches()) {
                tmuxStatusLine = plain;
                continue;
            }
            List<StyleTextPair> segments;
            if (!line.contains("\u001B") && !line.contains("[")) {
                segments = Collections.singletonList(new StyleTextPair(Style.EMPTY, null, line));
            } else {
                segments = parseKeywordsAndHighlight(line);
            }
            newWrappedLines.addAll(wrapStyledText(segments, wrapWidth));
        }
        synchronized (wrappedLinesCache) {
            wrappedLinesCache.clear();
            wrappedLinesCache.addAll(newWrappedLines);
        }
    }

    private static final Pattern ALL_ANSI = Pattern.compile("\u001B\\[[0-9;?]*(?:m|[A-Za-z])|\u001B=>|=\\u001B.*?\\\\|\\u001B]10;\\?\\\\|\\u001B]11;\\?\\\\|\u001B\\[\\?2004[hl]|\u001B=|\u001Bc|\u001B\\[\\?1h=\\u001B\\[\\?2004h|\u001B][0-9];.*?\u0007|\u001B][0-9];.*?\\\\|\u001BN|\u001BO|\u001BP[^\\\\]*\\\\|\u001B\\^|\u001B_|\u001B\\\\|\u001B]|\u001B[()][AB012]");

    private String removeAllAnsiSequences(String text) {
        if (text.indexOf('\u001B') < 0) {
            return text.replace("\t", "    ");
        }
        return ALL_ANSI.matcher(text).replaceAll("").replace("\t", "    ");
    }

    private List<StyleTextPair> parseKeywordsAndHighlight(String text) {
        text = text.replace("\u000f", "").replace("\t", "    ");
        List<StyleTextPair> result = new ArrayList<>();
        Matcher bracketMatcher = BRACKET_KEYWORD_PATTERN.matcher(text);
        int lastEnd = 0;
        while (bracketMatcher.find()) {
            if (bracketMatcher.start() > lastEnd) {
                String before = text.substring(lastEnd, bracketMatcher.start());
                result.addAll(parseAnsiAndHighlight(before));
            }
            String keyword = bracketMatcher.group(2).toUpperCase();
            TextColor keywordColor = switch (keyword) {
                case "WARNING", "WARN" -> TextColor.fromRgb(terminalTextWarnColor);
                case "ERROR" -> TextColor.fromRgb(terminalTextErrorColor);
                case "INFO" -> TextColor.fromRgb(terminalTextInfoColor);
                default -> TextColor.fromRgb(terminalTextColor);
            };
            Style keywordStyle = Style.EMPTY.withColor(keywordColor);
            String fullMatch = "[" + bracketMatcher.group(1) + bracketMatcher.group(2) + bracketMatcher.group(3) + "]";
            result.add(new StyleTextPair(keywordStyle, null, fullMatch));
            lastEnd = bracketMatcher.end();
        }
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            result.addAll(parseAnsiAndHighlight(remaining));
        }
        return result;
    }

    private List<StyleTextPair> parseAnsiAndHighlight(String text) {
        text = text.replace("\u000f", "").replace("\t", "    ");
        text = text.replaceAll("\u001B\\[[0-9;]*(?!m)[A-Za-z]", "");
        List<StyleTextPair> result = new ArrayList<>();
        AttributedString astring = AttributedString.fromAnsi(text);
        String plain = astring.toString();
        if (plain.isEmpty()) {
            return result;
        }
        AttributedStyle currentAttr = astring.styleAt(0);
        StringBuilder segmentBuilder = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            AttributedStyle attr = astring.styleAt(i);
            if (!attr.equals(currentAttr) && !segmentBuilder.isEmpty()) {
                result.add(new StyleTextPair(convertStyle(currentAttr), null, segmentBuilder.toString()));
                segmentBuilder = new StringBuilder();
                currentAttr = attr;
            }
            segmentBuilder.append(c);
        }
        if (!segmentBuilder.isEmpty()) {
            result.add(new StyleTextPair(convertStyle(currentAttr), null, segmentBuilder.toString()));
        }
        return result;
    }

    private Style convertStyle(AttributedStyle attr) {
        try {
            Field styleField = attr.getClass().getDeclaredField("style");
            styleField.setAccessible(true);
            int styleValue = styleField.getInt(attr);
            Field fForegroundField = attr.getClass().getDeclaredField("F_FOREGROUND");
            fForegroundField.setAccessible(true);
            int F_FOREGROUND = fForegroundField.getInt(attr);
            Field fgColorExpField = attr.getClass().getDeclaredField("FG_COLOR_EXP");
            fgColorExpField.setAccessible(true);
            int FG_COLOR_EXP = fgColorExpField.getInt(attr);
            if ((styleValue & F_FOREGROUND) != 0) {
                int index = (styleValue >> FG_COLOR_EXP) & 0xFF;
                int rgb = get256ColorRGB(index);
                return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            }
        } catch (Exception e) {
            devPrint("Error converting style" + e.getMessage());
        }
        return Style.EMPTY.withColor(TextColor.fromRgb(terminalTextColor));
    }

    private List<LineText> wrapStyledText(List<StyleTextPair> segments, int maxWidth) {
        List<LineText> wrappedLines = new ArrayList<>();
        if (maxWidth <= 0) return wrappedLines;
        List<StyleTextPair> currentLineSegments = new ArrayList<>();
        int currentLineWidth = 0;
        for (StyleTextPair segment : segments) {
            String text = segment.text;
            Style style = segment.style;
            int index = 0;
            while (index < text.length()) {
                int remainingWidth = maxWidth - currentLineWidth;
                int charsToFit = measureTextToFit(text.substring(index), remainingWidth);
                if (currentLineWidth > 0 && charsToFit == 0) {
                    wrappedLines.add(buildLineText(currentLineSegments));
                    currentLineSegments.clear();
                    currentLineWidth = 0;
                    continue;
                }
                if (charsToFit == 0 && currentLineWidth == 0) {
                    charsToFit = 1;
                }

                String substring = text.substring(index, index + charsToFit);
                currentLineSegments.add(new StyleTextPair(style, null, substring));
                currentLineWidth += mc.textRenderer.getWidth(substring);
                index += charsToFit;

                if (currentLineWidth >= maxWidth && index < text.length()) {
                    wrappedLines.add(buildLineText(currentLineSegments));
                    currentLineSegments.clear();
                    currentLineWidth = 0;
                }
            }
        }
        if (!currentLineSegments.isEmpty()) {
            wrappedLines.add(buildLineText(currentLineSegments));
        }
        return wrappedLines;
    }

    private int measureTextToFit(String text, int maxWidth) {
        int width = 0;
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            int charWidth = mc.textRenderer.getWidth(String.valueOf(c));
            if (width + charWidth > maxWidth) {
                break;
            }
            width += charWidth;
            index++;
        }
        return index;
    }

    private LineText buildLineText(List<StyleTextPair> segments) {
        MutableText lineText = Text.literal("");
        StringBuilder plainTextBuilder = new StringBuilder();
        for (StyleTextPair segment : segments) {
            Text styledText = Text.literal(segment.text).setStyle(segment.style);
            lineText.append(styledText);
            plainTextBuilder.append(segment.text);
        }
        return new LineText(lineText.asOrderedText(), plainTextBuilder.toString());
    }

    private int get256ColorRGB(int index) {
        if (index < 0 || index > 255) return 0xFFFFFF;
        if (index < 16) return getStandardColorRGB(index);

        if (index <= 231) {
            index -= 16;
            int r = (index / 36) % 6;
            int g = (index / 6) % 6;
            int b = index % 6;
            r *= 51;
            g *= 51;
            b *= 51;
            return (r << 16) | (g << 8) | b;
        } else {
            int gray = 8 + (index - 232) * 10;
            return (gray << 16) | (gray << 8) | gray;
        }
    }

    private int getStandardColorRGB(int index) {
        return switch (index) {
            case 0 -> 0x000000; case 1 -> 0xAA0000; case 2 -> 0x00AA00; case 3 -> 0xAA5500;
            case 4 -> 0x0000AA; case 5 -> 0xAA00AA; case 6 -> 0x00AAAA; case 7 -> 0xAAAAAA;
            case 8 -> 0x555555; case 9 -> 0xFF5555; case 10 -> 0x55FF55; case 11 -> 0xFFFF55;
            case 12 -> 0x5555FF; case 13 -> 0xFF55FF; case 14 -> 0x55FFFF;
            default -> 0xFFFFFF;
        };
    }

    private int getTotalLines() { synchronized (wrappedLinesCache) { return wrappedLinesCache.size(); } }
    private int getLineHeight() { return mc.textRenderer.fontHeight + 2; }
    private int getInputFieldHeight() { return showInputField ? mc.textRenderer.fontHeight - 2 : 0; }
    private int getStatusBarHeight() { return showStatusBar ? mc.textRenderer.fontHeight + 4 : 0; }
    private int getTextAreaHeight() {
        int padding = 2;
        return getHeight() - (2 * padding) - getInputFieldHeight() - getStatusBarHeight() - (showInputField ? 2 : 0);
    }
    private int getTotalScrollHeight() { return getTotalLines() * getLineHeight(); }

    private String[] getStatusBarStrings(int availableWidth) {
        if (customLeftStatus != null && customRightStatus != null) {
            return new String[]{customLeftStatus, customRightStatus};
        }
        if (terminalInstance == null) return new String[]{"", ""};

        String leftStatus, rightStatus;
        if (tmuxStatusLine.isEmpty()) {
            if (terminalInstance.getServerInfo() != null) {
                ServerInfo sInfo = terminalInstance.getServerInfo();
                String serverStatus;
                switch (sInfo.state) {
                    case STARTING -> serverStatus = "Starting";
                    case RUNNING -> serverStatus = "Running";
                    case STOPPED -> serverStatus = "Stopped";
                    case CRASHED -> serverStatus = "Crashed";
                    default -> serverStatus = "Unknown";
                }
                leftStatus = obfuscateIps("Remotely | " + sInfo.name + " - " + serverStatus);
                if (sInfo.remoteHost != null && sInfo.remoteSSHManager != null && sInfo.isRemote) {
                    boolean connected = sInfo.remoteSSHManager.isSSH();
                    rightStatus = obfuscateIps(connected ? sInfo.remoteHost.name + " - Connected" : sInfo.remoteHost.name + ": Disconnected");
                } else {
                    rightStatus = obfuscateIps("Local Host | " + new Date());
                }
            } else {
                leftStatus = "Remotely";
                rightStatus = new Date().toString();
            }
        } else {
            int idx = tmuxStatusLine.indexOf("     ");
            if (idx != -1) {
                leftStatus = obfuscateIps(tmuxStatusLine.substring(0, idx).trim());
                rightStatus = obfuscateIps(tmuxStatusLine.substring(idx).trim());
            } else {
                leftStatus = obfuscateIps(tmuxStatusLine);
                rightStatus = "";
            }
        }

        return new String[]{
                trimTextToWidthWithEllipsis(leftStatus, availableWidth / 2 - 4),
                trimTextToWidthWithEllipsis(rightStatus, availableWidth / 2 - 4)
        };
    }

    private String trimTextToWidthWithEllipsis(String text, int maxWidth) {
        if (mc.textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = mc.textRenderer.getWidth(ellipsis);
        String tempText = text;
        while (mc.textRenderer.getWidth(tempText) + ellipsisWidth > maxWidth && !tempText.isEmpty()) {
            tempText = tempText.substring(0, tempText.length() - 1);
        }
        return tempText + ellipsis;
    }

    private boolean isMouseOverTerminal(double mouseX, double mouseY) {
        int padding = 2;
        int textAreaX = getX() + padding;
        int textAreaY = getY() + padding;
        int textAreaWidth = getWidth() - (2 * padding);
        return mouseX >= textAreaX && mouseX <= textAreaX + textAreaWidth &&
                mouseY >= textAreaY && mouseY <= textAreaY + getTextAreaHeight();
    }

    private void updateSelectionStart(double mouseX, double mouseY) {
        if (wrappedLinesCache.isEmpty()) return;
        PointInTerminal point = getTerminalCoordinates(mouseX, mouseY);
        selectionStartLine = point.line;
        selectionStartChar = getCharIndexInLine(point.line, point.x);
        selectionEndLine = selectionStartLine;
        selectionEndChar = selectionStartChar;
    }

    private void updateSelectionEnd(double mouseX, double mouseY) {
        if (wrappedLinesCache.isEmpty()) return;
        PointInTerminal point = getTerminalCoordinates(mouseX, mouseY);
        selectionEndLine = point.line;
        selectionEndChar = getCharIndexInLine(point.line, point.x);
    }

    private PointInTerminal getTerminalCoordinates(double mouseX, double mouseY) {
        int padding = 2;
        int textAreaX = getX() + padding;
        int textAreaY = getY() + padding;
        int firstLine = (int) Math.floor(currentScrollOffset / getLineHeight());
        int offsetY = (int) ((mouseY - textAreaY) + (currentScrollOffset % getLineHeight()));
        int line = firstLine + offsetY / getLineHeight();
        line = Math.max(0, Math.min(line, getTotalLines() - 1));
        int relativeX = (int) (mouseX - textAreaX);
        return new PointInTerminal(line, relativeX);
    }

    private int getCharIndexInLine(int lineIndex, int relativeX) {
        synchronized(wrappedLinesCache) {
            if (lineIndex < 0 || lineIndex >= wrappedLinesCache.size()) return 0;
            String lineText = wrappedLinesCache.get(lineIndex).plainText;
            int charIndex = 0;
            int widthSum = 0;
            while (charIndex < lineText.length()) {
                int charWidth = mc.textRenderer.getWidth(String.valueOf(lineText.charAt(charIndex)));
                if (widthSum + charWidth / 2.0f >= relativeX) break;
                widthSum += charWidth;
                charIndex++;
            }
            return charIndex;
        }
    }

    private void scrollToEdgesTerminal(double mouseY) {
        int padding = 2;
        int textAreaY = getY() + padding;
        int textAreaHeight = getTextAreaHeight();
        double speedFactor = 0.1;
        double minDiff = 5.0;
        if (mouseY < textAreaY) {
            double diff = Math.max(textAreaY - mouseY, minDiff);
            targetScrollOffset = Math.max(0, targetScrollOffset - (float)(diff * speedFactor));
        } else if (mouseY > textAreaY + textAreaHeight) {
            double diff = Math.max(mouseY - (textAreaY + textAreaHeight), minDiff);
            int maxScroll = Math.max(0, getTotalScrollHeight() - textAreaHeight);
            targetScrollOffset = Math.min(maxScroll, targetScrollOffset + (float)(diff * speedFactor));
        }
    }

    private String getSelectedText() {
        if (selectionStartLine == -1 || selectionEndLine == -1) return "";

        int startL = selectionStartLine, endL = selectionEndLine;
        int startC = selectionStartChar, endC = selectionEndChar;

        if (startL > endL || (startL == endL && startC > endC)) {
            startL = selectionEndLine; endL = selectionStartLine;
            startC = selectionEndChar; endC = selectionStartChar;
        }

        StringBuilder sb = new StringBuilder();
        synchronized (wrappedLinesCache) {
            for (int i = startL; i <= endL; i++) {
                if (i < 0 || i >= wrappedLinesCache.size()) continue;
                String lineText = wrappedLinesCache.get(i).plainText;

                int lineStartChar = (i == startL) ? startC : 0;
                int lineEndChar = (i == endL) ? endC : lineText.length();

                if (lineStartChar >= lineText.length()) {
                    if (i < endL) sb.append('\n');
                    continue;
                }
                sb.append(lineText, lineStartChar, Math.min(lineEndChar, lineText.length()));
                if (i < endL) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void drawSelection(DrawContext context, LineInfo lineInfo, int x) {
        int startL = selectionStartLine, endL = selectionEndLine;
        int startC = selectionStartChar, endC = selectionEndChar;

        if (startL > endL || (startL == endL && startC > endC)) {
            startL = selectionEndLine; endL = selectionStartLine;
            startC = selectionEndChar; endC = selectionStartChar;
        }

        if (lineInfo.lineNumber < startL || lineInfo.lineNumber > endL) return;

        String lineText = lineInfo.plainText;
        int selectionStart = (lineInfo.lineNumber == startL) ? startC : 0;
        int selectionEnd = (lineInfo.lineNumber == endL) ? endC : lineText.length();

        if (selectionStart >= lineText.length()) return;

        int selectionXStart = x + mc.textRenderer.getWidth(lineText.substring(0, selectionStart));
        int selectionWidth = mc.textRenderer.getWidth(lineText.substring(selectionStart, Math.min(selectionEnd, lineText.length())));

        context.fill(selectionXStart, lineInfo.y, selectionXStart + selectionWidth, lineInfo.y + getLineHeight(), globalSelectionColor);
    }

    private boolean isLineSelected(int lineNumber) {
        if (selectionStartLine == -1 || selectionEndLine == -1) return false;
        int start = Math.min(selectionStartLine, selectionEndLine);
        int end = Math.max(selectionStartLine, selectionEndLine);
        return lineNumber >= start && lineNumber <= end;
    }

    private static String obfuscateIps(String input) {
        if (showIp) return input;
        return IP_PATTERN.matcher(input).replaceAll("§k$1§r");
    }

    private record StyleTextPair(Style style, TextColor backgroundColor, String text) { }

    private record LineText(OrderedText orderedText, String plainText) {}
    private record PointInTerminal(int line, int x) {}

    public static class LineInfo {
        final int lineNumber, y, height; final OrderedText orderedText; final String plainText;
        LineInfo(int lineNumber, int y, int height, OrderedText orderedText, String plainText) {
            this.lineNumber = lineNumber; this.y = y; this.height = height;
            this.orderedText = orderedText; this.plainText = plainText;
        }
    }
}