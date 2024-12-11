package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.*;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalRenderer {

    private final MinecraftClient minecraftClient;
    private final TerminalInstance terminalInstance;
    private final StringBuilder terminalOutput = new StringBuilder();
    private float scale = 1.0f;
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 2.0f;
    private int scrollOffset = 0;
    private long lastBlinkTime = 0;
    private boolean cursorVisible = true;
    private long lastInputTime = 0;

    private final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern TMUX_STATUS_PATTERN = Pattern.compile("^\\[\\d+].*");
    private final List<UrlInfo> urlInfos = new ArrayList<>();
    private final Pattern BRACKET_KEYWORD_PATTERN = Pattern.compile("\\[(.*?)\\b(WARNING|WARN|ERROR|INFO)\\b(.*?)]");
    private final Map<String, TextColor> keywordColors = new HashMap<>();

    private final List<LineInfo> lineInfos = new ArrayList<>();

    private boolean isSelecting = false;
    private int selectionStartLine = -1;
    private int selectionStartChar = -1;
    private int selectionEndLine = -1;
    private int selectionEndChar = -1;

    private int terminalX;
    private int terminalY;
    private int terminalWidth;
    private int terminalHeight;

    private UrlInfo hoveredUrl = null;
    private String tmuxStatusLine = "";

    public TerminalRenderer(MinecraftClient client, TerminalInstance terminalInstance) {
        this.minecraftClient = client;
        this.terminalInstance = terminalInstance;
        keywordColors.put("WARNING", TextColor.fromRgb(0xFFA500));
        keywordColors.put("WARN", TextColor.fromRgb(0xFFA500));
        keywordColors.put("ERROR", TextColor.fromRgb(0xFF0000));
        keywordColors.put("INFO", TextColor.fromRgb(0x00FF00));
    }

    public void render(DrawContext context, int mouseX, int mouseY, int screenWidth, int screenHeight, float scale) {
        this.scale = Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));

        this.terminalX = 10;
        this.terminalY = MultiTerminalScreen.TAB_HEIGHT + 10;
        this.terminalWidth = screenWidth - 20;
        this.terminalHeight = screenHeight - terminalY - 10;

        context.fill(terminalX, terminalY, terminalX + terminalWidth, terminalY + terminalHeight, 0xFF000000);

        int padding = 2;
        int textAreaX = terminalX + padding;
        int textAreaY = terminalY + padding;
        int textAreaWidth = terminalWidth - 2 * padding;
        int textAreaHeight = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();

        context.getMatrices().push();
        context.getMatrices().translate(textAreaX, textAreaY, 0);
        context.getMatrices().scale(this.scale, this.scale, 1.0f);

        int scaledWidth = (int) (textAreaWidth / this.scale);
        int scaledHeight = (int) (textAreaHeight / this.scale);
        int x = 0;
        int yStart = 0;

        List<String> lines;
        synchronized (terminalOutput) {
            lines = Arrays.asList(terminalOutput.toString().split("\n", -1));
        }
        List<LineText> allWrappedLines = new ArrayList<>();
        tmuxStatusLine = "";
        for (String line : lines) {
            line = removeAllControlSequences(line);
            if (line.trim().equals(">")) {
                continue;
            }
            Matcher tmuxMatcher = TMUX_STATUS_PATTERN.matcher(line);
            if (tmuxMatcher.matches()) {
                tmuxStatusLine = removeAllAnsiSequences(line.trim()).replace("\u000f", "");
                continue;
            }
            List<StyleTextPair> segments = parseKeywordsAndHighlight(line);
            List<LineText> wrappedLines = wrapStyledText(segments, scaledWidth);
            allWrappedLines.addAll(wrappedLines);
        }

        int totalLines = allWrappedLines.size();
        int visibleLines = getVisibleLines(scaledHeight);

        scrollOffset = Math.min(scrollOffset, Math.max(0, totalLines - visibleLines));

        int startLine = Math.max(0, totalLines - visibleLines - scrollOffset);
        int endLine = Math.min(totalLines, startLine + visibleLines);

        lineInfos.clear();
        urlInfos.clear();

        for (int i = startLine; i < endLine; i++) {
            LineText lineText = allWrappedLines.get(i);
            int lineHeight = minecraftClient.textRenderer.fontHeight;
            LineInfo lineInfo = new LineInfo(i, yStart, lineHeight, lineText.orderedText, lineText.plainText);
            lineInfos.add(lineInfo);
            if (isLineSelected(i)) {
                drawSelection(context, lineInfo, x);
            }
            context.drawText(minecraftClient.textRenderer, lineText.orderedText, x, yStart, 0xFFFFFFFF, false);
            yStart += lineHeight;
        }

        context.getMatrices().pop();

        int inputX = terminalX + padding;
        int inputY = terminalY + terminalHeight - padding - getInputFieldHeight() - getStatusBarHeight();
        String inputPrompt = terminalInstance.getSSHManager().isAwaitingPassword() ? "Password: " : "> ";
        String inputText = inputPrompt + terminalInstance.inputHandler.getInputBuffer().toString();

        context.drawText(minecraftClient.textRenderer, Text.literal(inputText), inputX, inputY, 0x4AF626, false);

        String suggestion = terminalInstance.inputHandler.getTabCompletionSuggestion();
        if (!suggestion.isEmpty() && !terminalInstance.inputHandler.getInputBuffer().isEmpty()) {
            int inputTextWidth = minecraftClient.textRenderer.getWidth(inputText);
            context.drawText(minecraftClient.textRenderer, Text.literal(suggestion).setStyle(Style.EMPTY.withColor(0x666666)), inputX + inputTextWidth, inputY, 0x666666, false);
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInputTime < 500) {
            cursorVisible = true;
        } else if (currentTime - lastBlinkTime > 500) {
            cursorVisible = !cursorVisible;
            lastBlinkTime = currentTime;
        }
        if (cursorVisible) {
            int cursorInputPosition = Math.min(terminalInstance.inputHandler.getCursorPosition(), terminalInstance.inputHandler.getInputBuffer().length());
            String beforeCursor = inputPrompt + terminalInstance.inputHandler.getInputBuffer().substring(0, cursorInputPosition);
            int cursorXPos = inputX + minecraftClient.textRenderer.getWidth(beforeCursor);
            int cursorHeight = minecraftClient.textRenderer.fontHeight;
            context.fill(cursorXPos, inputY, cursorXPos + 1, inputY + cursorHeight, 0xFFFFFFFF);
        }

        handleUrlHover(mouseX, mouseY);

        int statusBarY = terminalY + terminalHeight - getStatusBarHeight();
        context.fill(terminalX, statusBarY, terminalX + terminalWidth, statusBarY + getStatusBarHeight(), 0xFF333333);

        OrderedText[] statusTexts = getStatusBarOrderedTexts(scaledWidth);
        OrderedText leftStatus = statusTexts[0];
        OrderedText rightStatus = statusTexts[1];
        int rightWidth = minecraftClient.textRenderer.getWidth(rightStatus);

        context.drawText(minecraftClient.textRenderer, leftStatus, terminalX + 2, statusBarY + (getStatusBarHeight() - minecraftClient.textRenderer.fontHeight) / 2, 0xFFFFFFFF, false);
        context.drawText(minecraftClient.textRenderer, rightStatus, terminalX + terminalWidth - 2 - rightWidth, statusBarY + (getStatusBarHeight() - minecraftClient.textRenderer.fontHeight) / 2, 0xFFFFFFFF, false);
    }

    private String removeAllControlSequences(String text) {
        if (!terminalInstance.getSSHManager().isSSH()) {
            return text;
        }
        Matcher matcher = ANSI_PATTERN.matcher(text);
        return matcher.replaceAll("");
    }

    private String removeAllAnsiSequences(String text) {
        if (!terminalInstance.getSSHManager().isSSH()) {
            return text;
        }
        Matcher matcher = ANSI_PATTERN.matcher(text);
        return matcher.replaceAll("");
    }

    public void resetCursorBlink() {
        lastInputTime = System.currentTimeMillis();
        cursorVisible = true;
    }

    private List<StyleTextPair> parseKeywordsAndHighlight(String text) {
        text = text.replace("\u000f", "");
        List<StyleTextPair> result = new ArrayList<>();

        Matcher bracketMatcher = BRACKET_KEYWORD_PATTERN.matcher(text);
        int lastEnd = 0;
        while (bracketMatcher.find()) {
            if (bracketMatcher.start() > lastEnd) {
                String before = text.substring(lastEnd, bracketMatcher.start());
                result.addAll(parseAnsiAndHighlight(before));
            }
            String keyword = bracketMatcher.group(2).toUpperCase();
            TextColor keywordColor = keywordColors.getOrDefault(keyword, TextColor.fromRgb(0xFFFFFF));
            Style keywordStyle = Style.EMPTY.withColor(keywordColor);
            String fullMatch = "[" + bracketMatcher.group(1) + bracketMatcher.group(2) + bracketMatcher.group(3) + "]";
            result.add(new StyleTextPair(keywordStyle, null, fullMatch));
            result.add(new StyleTextPair(Style.EMPTY, null, ""));
            lastEnd = bracketMatcher.end();
        }
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            result.addAll(parseAnsiAndHighlight(remaining));
        }
        return result;
    }

    private List<StyleTextPair> parseAnsiAndHighlight(String text) {
        text = text.replace("\u000f", "");
        List<StyleTextPair> result = new ArrayList<>();
        Matcher matcher = ANSI_PATTERN.matcher(text);
        int lastEnd = 0;
        Style currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF));
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                if (!before.isEmpty()) {
                    result.add(new StyleTextPair(currentStyle, null, before));
                }
            }
            String ansiSequence = matcher.group();
            String codeContent = ansiSequence.substring(2, ansiSequence.length() - 1);
            currentStyle = applyAnsiCodes(currentStyle, codeContent);
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                result.add(new StyleTextPair(currentStyle, null, remaining));
            }
        }
        return result;
    }

    private Style applyAnsiCodes(Style style, String code) {
        String[] codes = code.split(";");
        int i = 0;
        while (i < codes.length) {
            String c = codes[i];
            int codeNum;
            try {
                codeNum = Integer.parseInt(c.replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                i++;
                continue;
            }
            switch (codeNum) {
                case 0 -> style = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withItalic(false).withUnderline(false);
                case 1 -> {}
                case 3 -> style = style.withItalic(true);
                case 4 -> style = style.withUnderline(true);
                case 22 -> {}
                case 23 -> style = style.withItalic(false);
                case 24 -> style = style.withUnderline(false);
                case 27 -> {}
                case 38, 48 -> {
                    if (i + 1 < codes.length) {
                        if ("2".equals(codes[i + 1])) {
                            if (i + 4 < codes.length) {
                                try {
                                    int r = Integer.parseInt(codes[i + 2]);
                                    int g = Integer.parseInt(codes[i + 3]);
                                    int b = Integer.parseInt(codes[i + 4]);
                                    TextColor color = TextColor.fromRgb((r << 16) | (g << 8) | b);
                                    if (codeNum == 38) {
                                        style = style.withColor(color);
                                    }
                                    i += 4;
                                    continue;
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        } else if ("5".equals(codes[i + 1])) {
                            if (i + 2 < codes.length) {
                                try {
                                    int colorIndex = Integer.parseInt(codes[i + 2]);
                                    TextColor color = TextColor.fromRgb(get256ColorRGB(colorIndex));
                                    if (codeNum == 38) {
                                        style = style.withColor(color);
                                    }
                                    i += 2;
                                    continue;
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                }
                default -> {
                    if (codeNum >= 30 && codeNum <= 37) {
                        TextColor color = getStandardColor(codeNum - 30);
                        style = style.withColor(color);
                    } else if (codeNum >= 90 && codeNum <= 97) {
                        TextColor color = getBrightColor(codeNum - 90);
                        style = style.withColor(color);
                    }
                }
            }
            i++;
        }
        return style;
    }

    private List<LineText> wrapStyledText(List<StyleTextPair> segments, int maxWidth) {
        List<LineText> wrappedLines = new ArrayList<>();
        List<StyleTextPair> currentLineSegments = new ArrayList<>();
        int currentLineWidth = 0;

        for (StyleTextPair segment : segments) {
            String text = segment.text;
            Style style = segment.style;
            String url = segment.url;
            int index = 0;
            while (index < text.length()) {
                int remainingWidth = maxWidth - currentLineWidth;
                int charsToFit = measureTextToFit(text.substring(index), remainingWidth);
                if (charsToFit == 0) {
                    if (!currentLineSegments.isEmpty()) {
                        LineText lineText = buildLineText(currentLineSegments);
                        wrappedLines.add(lineText);
                        currentLineSegments.clear();
                    }
                    currentLineWidth = 0;
                    charsToFit = Math.max(1, measureTextToFit(text.substring(index), maxWidth));
                }
                String substring = text.substring(index, index + charsToFit);
                currentLineSegments.add(new StyleTextPair(style, null, substring, url));
                int width = minecraftClient.textRenderer.getWidth(substring);
                currentLineWidth += width;
                index += charsToFit;

                if (currentLineWidth >= maxWidth) {
                    LineText lineText = buildLineText(currentLineSegments);
                    wrappedLines.add(lineText);
                    currentLineSegments.clear();
                    currentLineWidth = 0;
                }
            }
        }
        if (!currentLineSegments.isEmpty()) {
            LineText lineText = buildLineText(currentLineSegments);
            wrappedLines.add(lineText);
        }
        return wrappedLines;
    }

    private int measureTextToFit(String text, int maxWidth) {
        int width = 0;
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            int charWidth = minecraftClient.textRenderer.getWidth(String.valueOf(c));
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
        int xOffset = 0;
        for (StyleTextPair segment : segments) {
            Text styledText = Text.literal(segment.text).setStyle(segment.style);
            lineText.append(styledText);
            plainTextBuilder.append(segment.text);
            if (segment.url != null) {
                int width = minecraftClient.textRenderer.getWidth(segment.text);
                UrlInfo urlInfo = new UrlInfo(segment.url, xOffset, xOffset + width);
                urlInfos.add(urlInfo);
                xOffset += width;
            } else {
                xOffset += minecraftClient.textRenderer.getWidth(segment.text);
            }
        }
        return new LineText(lineText.asOrderedText(), plainTextBuilder.toString());
    }

    private int get256ColorRGB(int index) {
        if (index < 0 || index > 255) {
            return 0xFFFFFF;
        }
        if (index < 16) {
            return getStandardColorRGB(index);
        } else if (index <= 231) {
            index -= 16;
            int r = (index / 36) % 6;
            int g = (index / 6) % 6;
            int b = index % 6;
            r = r * 51;
            g = g * 51;
            b = b * 51;
            return (r << 16) | (g << 8) | b;
        } else {
            int gray = 8 + (index - 232) * 10;
            return (gray << 16) | (gray << 8) | gray;
        }
    }

    private TextColor getStandardColor(int index) {
        return switch (index) {
            case 0 -> TextColor.fromRgb(0x000000);
            case 1 -> TextColor.fromRgb(0xAA0000);
            case 2 -> TextColor.fromRgb(0x00AA00);
            case 3 -> TextColor.fromRgb(0xAA5500);
            case 4 -> TextColor.fromRgb(0x0000AA);
            case 5 -> TextColor.fromRgb(0xAA00AA);
            case 6 -> TextColor.fromRgb(0x00AAAA);
            case 7 -> TextColor.fromRgb(0xAAAAAA);
            default -> TextColor.fromRgb(0xFFFFFF);
        };
    }

    private TextColor getBrightColor(int index) {
        return switch (index) {
            case 0 -> TextColor.fromRgb(0x555555);
            case 1 -> TextColor.fromRgb(0xFF5555);
            case 2 -> TextColor.fromRgb(0x55FF55);
            case 3 -> TextColor.fromRgb(0xFFFF55);
            case 4 -> TextColor.fromRgb(0x5555FF);
            case 5 -> TextColor.fromRgb(0xFF55FF);
            case 6 -> TextColor.fromRgb(0x55FFFF);
            case 7 -> TextColor.fromRgb(0xFFFFFF);
            default -> TextColor.fromRgb(0xFFFFFF);
        };
    }

    private int getStandardColorRGB(int index) {
        return switch (index) {
            case 0 -> 0x000000;
            case 1 -> 0xAA0000;
            case 2 -> 0x00AA00;
            case 3 -> 0xAA5500;
            case 4 -> 0x0000AA;
            case 5 -> 0xAA00AA;
            case 6 -> 0x00AAAA;
            case 7 -> 0xAAAAAA;
            case 8 -> 0x555555;
            case 9 -> 0xFF5555;
            case 10 -> 0x55FF55;
            case 11 -> 0xFFFF55;
            case 12 -> 0x5555FF;
            case 13 -> 0xFF55FF;
            case 14 -> 0x55FFFF;
            default -> 0xFFFFFF;
        };
    }

    private int getTotalLines(int maxWidth) {
        String[] allLines;
        synchronized (terminalOutput) {
            allLines = terminalOutput.toString().split("\n", -1);
        }
        int totalWrappedLines = 0;
        for (String line : allLines) {
            line = removeAllControlSequences(line);
            if (line.trim().equals(">")) {
                continue;
            }
            Matcher tmuxMatcher = TMUX_STATUS_PATTERN.matcher(line);
            if (tmuxMatcher.matches()) {
                continue;
            }
            List<StyleTextPair> segments = parseKeywordsAndHighlight(line);
            List<LineText> wrappedLines = wrapStyledText(segments, maxWidth);
            totalWrappedLines += wrappedLines.size();
        }
        return totalWrappedLines;
    }

    private int getVisibleLines(int scaledHeight) {
        int visibleHeight = Math.max(scaledHeight - getInputFieldHeight(), 1);
        return Math.max((visibleHeight - getStatusBarHeight()) / minecraftClient.textRenderer.fontHeight, 1);
    }

    int getInputFieldHeight() {
        return minecraftClient.textRenderer.fontHeight + 4;
    }

    int getStatusBarHeight() {
        return minecraftClient.textRenderer.fontHeight + 4;
    }

    private OrderedText[] getStatusBarOrderedTexts(int scaledWidth) {
        if (tmuxStatusLine.isEmpty()) {
            return new OrderedText[]{Text.literal("Active session").asOrderedText(), Text.literal(new Date().toString()).asOrderedText()};
        }
        String line = tmuxStatusLine;
        String leftText;
        String rightText;
        int idx = line.indexOf("     ");
        if (idx != -1) {
            leftText = line.substring(0, idx).trim();
            rightText = line.substring(idx).trim();
        } else {
            leftText = line;
            rightText = "";
        }

        List<StyleTextPair> leftSegments = parseKeywordsAndHighlight(leftText);
        List<LineText> leftWrapped = wrapStyledText(leftSegments, scaledWidth);
        OrderedText leftOrdered = leftWrapped.isEmpty() ? Text.literal(leftText).asOrderedText() : leftWrapped.getFirst().orderedText;

        List<StyleTextPair> rightSegments = parseKeywordsAndHighlight(rightText);
        List<LineText> rightWrapped = wrapStyledText(rightSegments, scaledWidth);
        OrderedText rightOrdered = rightWrapped.isEmpty() ? Text.literal(rightText).asOrderedText() : rightWrapped.getFirst().orderedText;

        return new OrderedText[]{leftOrdered, rightOrdered};
    }

    public void appendOutput(String text) {
        text = text.replace("\r", "");
        synchronized (terminalOutput) {
            terminalOutput.append(text);
            lineInfos.clear();
        }
        minecraftClient.execute(terminalInstance.parentScreen::init);
    }

    public void scroll(int direction, int scaledHeight) {
        int maxWidth = (int) ((terminalWidth - 10) / scale);
        int totalLines = getTotalLines(maxWidth);
        int visibleLines = getVisibleLines(scaledHeight);
        if (direction > 0) {
            if (scrollOffset < totalLines - visibleLines) {
                scrollOffset += MultiTerminalScreen.SCROLL_STEP;
            }
        } else if (direction < 0) {
            if (scrollOffset > 0) {
                scrollOffset -= MultiTerminalScreen.SCROLL_STEP;
            }
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, totalLines - visibleLines));
    }

    public void scrollToTop(int scaledHeight) {
        int maxWidth = (int) ((terminalWidth - 10) / scale);
        int totalLines = getTotalLines(maxWidth);
        int visibleLines = getVisibleLines(scaledHeight);
        scrollOffset = totalLines - visibleLines;
        scrollOffset = Math.max(0, scrollOffset);
    }

    public void scrollToBottom() {
        scrollOffset = 0;
    }

    public StringBuilder getTerminalOutput() {
        return terminalOutput;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isMouseOverTerminal(mouseX, mouseY)) {
                if (hoveredUrl != null && InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
                    openUrl(hoveredUrl.url);
                    return true;
                }
                isSelecting = true;
                updateSelectionStart(mouseX, mouseY);
                updateSelectionEnd(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isSelecting) {
                isSelecting = false;
                updateSelectionEnd(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateSelectionEnd(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private boolean isMouseOverTerminal(double mouseX, double mouseY) {
        return mouseX >= terminalX + 5 && mouseX <= terminalX + terminalWidth - 5 &&
                mouseY >= terminalY + 5 && mouseY <= terminalY + terminalHeight - getInputFieldHeight() - getStatusBarHeight() - 5;
    }

    private void updateSelectionStart(double mouseX, double mouseY) {
        int lineIndex = getLineIndexAtPosition(mouseY);
        if (lineIndex != -1) {
            int charIndex = getCharIndexAtPosition(mouseX, lineIndex);
            selectionStartLine = lineIndex;
            selectionStartChar = charIndex;
            selectionEndLine = lineIndex;
            selectionEndChar = charIndex;
        }
    }

    private void updateSelectionEnd(double mouseX, double mouseY) {
        int lineIndex = getLineIndexAtPosition(mouseY);
        if (lineIndex != -1) {
            int charIndex = getCharIndexAtPosition(mouseX, lineIndex);
            selectionEndLine = lineIndex;
            selectionEndChar = charIndex;
        }
    }

    private int getLineIndexAtPosition(double mouseY) {
        double relativeY = mouseY - terminalY - 5;
        relativeY /= scale;
        for (LineInfo lineInfo : lineInfos) {
            if (relativeY >= lineInfo.y && relativeY < lineInfo.y + lineInfo.height) {
                return lineInfo.lineNumber;
            }
        }
        return -1;
    }

    private int getCharIndexAtPosition(double mouseX, int lineIndex) {
        LineInfo lineInfo = null;
        for (LineInfo li : lineInfos) {
            if (li.lineNumber == lineIndex) {
                lineInfo = li;
                break;
            }
        }
        if (lineInfo == null) return -1;
        double relativeX = mouseX - terminalX - 5;
        relativeX /= scale;
        String lineText = lineInfo.plainText;
        int charIndex = 0;
        int x = 0;
        for (char c : lineText.toCharArray()) {
            int charWidth = minecraftClient.textRenderer.getWidth(String.valueOf(c));
            if (x + (double) charWidth / 2 > relativeX) {
                return charIndex;
            }
            x += charWidth;
            charIndex++;
        }
        return charIndex;
    }

    private boolean isLineSelected(int lineNumber) {
        if (selectionStartLine == -1 || selectionEndLine == -1) {
            return false;
        }
        int startLine = Math.min(selectionStartLine, selectionEndLine);
        int endLine = Math.max(selectionStartLine, selectionEndLine);
        return lineNumber >= startLine && lineNumber <= endLine;
    }

    private void drawSelection(DrawContext context, LineInfo lineInfo, int x) {
        int lineNumber = lineInfo.lineNumber;
        int yPosition = lineInfo.y;
        String lineText = lineInfo.plainText;
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
        int selectionXStart = x + minecraftClient.textRenderer.getWidth(beforeSelection);
        int selectionWidth = minecraftClient.textRenderer.getWidth(selectionText);
        int selectionYEnd = yPosition + minecraftClient.textRenderer.fontHeight;

        context.fill(selectionXStart, yPosition, selectionXStart + selectionWidth, selectionYEnd, 0x80FFFFFF);
    }

    public void copySelectionToClipboard() {
        String selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            minecraftClient.keyboard.setClipboard(selectedText);
        }
        selectionStartLine = -1;
        selectionStartChar = -1;
        selectionEndLine = -1;
        selectionEndChar = -1;
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
            int tempLine = startLine;
            startLine = endLine;
            endLine = tempLine;
            int tempChar = startChar;
            startChar = endChar;
            endChar = tempChar;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            LineInfo lineInfo = null;
            for (LineInfo li : lineInfos) {
                if (li.lineNumber == i) {
                    lineInfo = li;
                    break;
                }
            }
            if (lineInfo == null) continue;
            String lineText = lineInfo.plainText;
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
            sb.append(lineText, lineStartChar, lineEndChar);
            if (i != endLine) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private void handleUrlHover(double mouseX, double mouseY) {
        hoveredUrl = null;
        if (!isMouseOverTerminal(mouseX, mouseY)) {
            return;
        }
        int lineIndex = getLineIndexAtPosition(mouseY);
        if (lineIndex == -1 || lineIndex >= lineInfos.size()) {
            return;
        }
        double relativeX = mouseX - terminalX - 5;
        relativeX /= scale;
        for (UrlInfo urlInfo : urlInfos) {
            if (relativeX >= urlInfo.startX && relativeX <= urlInfo.endX) {
                hoveredUrl = urlInfo;
                break;
            }
        }
    }

    private void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            terminalInstance.appendOutput("Failed to open URL: " + e.getMessage() + "\n");
        }
    }

    private static class StyleTextPair {
        final Style style;
        final TextColor backgroundColor;
        final String text;
        final String url;

        StyleTextPair(Style style, TextColor backgroundColor, String text) {
            this(style, backgroundColor, text, null);
        }

        StyleTextPair(Style style, TextColor backgroundColor, String text, String url) {
            this.style = style;
            this.backgroundColor = backgroundColor;
            this.text = text;
            this.url = url;
        }
    }

    private record LineText(OrderedText orderedText, String plainText) {
    }

    public static class LineInfo {
        final int lineNumber;
        final int y;
        final int height;
        final OrderedText orderedText;
        final String plainText;

        LineInfo(int lineNumber, int y, int height, OrderedText orderedText, String plainText) {
            this.lineNumber = lineNumber;
            this.y = y;
            this.height = height;
            this.orderedText = orderedText;
            this.plainText = plainText;
        }
    }

    private record UrlInfo(String url, int startX, int endX) {
    }
}