package redxax.oxy.common.terminal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.*;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.Render;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.servers.ServerInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

import static redxax.oxy.common.RemotelyClient.os;
import static redxax.oxy.common.Render.drawInnerBorder;
import static redxax.oxy.common.Render.drawOuterBorder;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.DevUtil.devPrint;

public class TerminalRenderer {
    public static TerminalRenderer instance;
    private final MinecraftClient minecraftClient;
    private final TerminalInstance terminalInstance;
    private final StringBuilder terminalOutput = new StringBuilder();
    private final List<LineText> wrappedLinesCache = new ArrayList<>();
    private int terminalWidth;
    public int scrollOffset = 0;
    private static final Pattern TMUX_STATUS_PATTERN = Pattern.compile(".*\\d{1,2}:\\d{2}\\s\\d{2}-[A-Za-z]{3}-\\d{2}.*");
    private final Pattern BRACKET_KEYWORD_PATTERN = Pattern.compile("\\[(.*?)\\b(WARNING|WARN|ERROR|INFO)\\b(.*?)]");
    private boolean isSelecting = false;
    private int selectionStartLine = -1;
    private int selectionStartChar = -1;
    private int selectionEndLine = -1;
    private int selectionEndChar = -1;
    private int terminalX;
    private int terminalY;
    public int terminalHeight;
    private String tmuxStatusLine = "";
    private float currentScrollOffset = 0;
    public float targetScrollOffset = 0;
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;?]*(?!m)[A-Za-z]");
    private static final Pattern ANSI_PATTERN2 = Pattern.compile("\u001B=>");
    private static final Pattern EXTRA_ANSI_PATTERN = Pattern.compile("=\\u001B.*?\\\\");
    static {
        System.setProperty("jline.ansi", "true");
        System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
        System.setProperty("jansi.passthrough", "true");
        System.setProperty("jansi.force", "true");
        System.setProperty("jansi.strip", "false");
        System.setProperty("jansi.disable", "false");
        System.setProperty("net.kyori.ansi.colorLevel", "indexed256");
    }

    private int lastTerminalWidth = terminalWidth;

    public TerminalRenderer(MinecraftClient client, TerminalInstance terminalInstance) {
        this.minecraftClient = client;
        this.terminalInstance = terminalInstance;
        instance = this;
    }

    public void render(DrawContext context, int screenWidth, int screenHeight) {
        terminalX = 5;
        terminalY = MultiTerminalScreen.ContentYStart;
        terminalWidth = screenWidth - 5;
        terminalHeight = screenHeight - terminalY - 15;
        int padding = 2;
        context.fill(terminalX, terminalY, terminalX + terminalWidth, terminalY + terminalHeight, Config.backgroundColor);
        drawInnerBorder(context, terminalX, terminalY, terminalWidth, terminalHeight, Config.innerBorderColor);
        drawOuterBorder(context, terminalX, terminalY, terminalWidth, terminalHeight, globalOuterBorder);
        int textAreaX = terminalX + padding;
        int textAreaY2 = terminalY + padding;
        int textAreaWidth = terminalWidth - 2 * padding;
        int textAreaHeight2 = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        int totalLinesRender = getTotalLines();
        int maxScroll = Math.max(0, totalLinesRender * lineHeight - textAreaHeight2);
        float deltaScroll = targetScrollOffset - currentScrollOffset;
        currentScrollOffset += deltaScroll * globalScrollSpeed * deltaTime;
        if (currentScrollOffset < 0) {
            currentScrollOffset += (-currentScrollOffset) * 0.3f;
        } else if (currentScrollOffset > maxScroll) {
            currentScrollOffset -= (currentScrollOffset - maxScroll) * 0.3f;
        }
        scrollOffset = (int) currentScrollOffset;
        context.enableScissor(textAreaX, textAreaY2, textAreaX + textAreaWidth, textAreaY2 + textAreaHeight2);
        int firstLine = (int) Math.floor(currentScrollOffset / lineHeight);
        int visibleLines = textAreaHeight2 / lineHeight + 3;
        for (int i = 0; i < visibleLines; i++) {
            int lineIndex = firstLine + i;
            if (lineIndex < 0 || lineIndex >= totalLinesRender)
                continue;
            int renderY = textAreaY2 + i * lineHeight - ((int) currentScrollOffset % lineHeight);
            LineText lineText;
            synchronized (wrappedLinesCache) {
                if (lineIndex >= wrappedLinesCache.size()) {
                    continue;
                }
                lineText = wrappedLinesCache.get(lineIndex);
            }
            if (isLineSelected(lineIndex)) {
                LineInfo tempLineInfo = new LineInfo(lineIndex, renderY, minecraftClient.textRenderer.fontHeight, lineText.orderedText, lineText.plainText);
                drawSelection(context, tempLineInfo, textAreaX);
            }
            context.drawText(minecraftClient.textRenderer, lineText.orderedText, textAreaX, renderY, terminalTextColor, Config.shadow);
        }
        context.disableScissor();
        int statusBarY = terminalY + terminalHeight - getStatusBarHeight();
        int inputY = statusBarY - 2 - getInputFieldHeight();
        int inputX = terminalX + padding;
        String inputPrompt = terminalInstance.getSSHManager().isAwaitingPassword() ? "Password: " : "> ";
        String inputText = inputPrompt + terminalInstance.inputHandler.getInputBuffer().toString();
        context.drawText(minecraftClient.textRenderer, Text.literal(inputText), inputX, inputY, terminalTextInputColor, Config.shadow);
        String suggestion = terminalInstance.inputHandler.getTabCompletionSuggestion();
        if (!suggestion.isEmpty() && !terminalInstance.inputHandler.getInputBuffer().isEmpty()) {
            int inputTextWidth = minecraftClient.textRenderer.getWidth(inputText);
            context.drawText(minecraftClient.textRenderer, Text.literal(suggestion).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Config.globalDarkTextColor))), inputX + inputTextWidth, inputY, Config.globalDarkTextColor, Config.shadow);
        }
        int cursorInputPosition = Math.min(terminalInstance.inputHandler.getCursorPosition(), terminalInstance.inputHandler.getInputBuffer().length());
        String beforeCursor = inputPrompt + terminalInstance.inputHandler.getInputBuffer().substring(0, cursorInputPosition);
        int cursorXPos = inputX + minecraftClient.textRenderer.getWidth(beforeCursor);
        int cursorHeight = minecraftClient.textRenderer.fontHeight;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1000);
        context.fill(cursorXPos, inputY, cursorXPos + 1, inputY + cursorHeight, globalCursorAnimatedColor);
        context.getMatrices().pop();

        context.fill(terminalX, statusBarY, terminalX + terminalWidth, statusBarY + getStatusBarHeight(), terminalStatusBarColor);
        OrderedText[] statusTexts = getStatusBarOrderedTexts(textAreaWidth);
        OrderedText leftStatus = statusTexts[0];
        OrderedText rightStatus = statusTexts[1];
        int rightWidth = minecraftClient.textRenderer.getWidth(rightStatus);
        context.drawText(minecraftClient.textRenderer, leftStatus, terminalX + 2, statusBarY + (getStatusBarHeight() - minecraftClient.textRenderer.fontHeight) / 2, terminalTextColor, Config.shadow);
        context.drawText(minecraftClient.textRenderer, rightStatus, terminalX + terminalWidth - 2 - rightWidth, statusBarY + (getStatusBarHeight() - minecraftClient.textRenderer.fontHeight) / 2, terminalTextColor, Config.shadow);
        if (terminalWidth != lastTerminalWidth) {
            stickToBottom(8);
            rewrap();
            lastTerminalWidth = terminalWidth;
        }
    }

    public void rewrap() {
        List<LineText> newWrappedLines = new ArrayList<>();
        String output;
        synchronized (terminalOutput) {
            output = terminalOutput.toString();
        }
        Pattern extraPattern = Pattern.compile("^(.*\\d{1,2}:\\d{2}\\s\\d{2}-[A-Za-z]{3}-)\\d{2}(.*)$");
        String[] lines = output.split("\n", -1);
        int wrapWidth = terminalWidth - 10;
        for (String rawLine : lines) {
            if (rawLine.isEmpty()) continue;
            String line = rawLine.replace("\0", "");
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

    private Style convertStyle(org.jline.utils.AttributedStyle attr) {
        try {
            java.lang.reflect.Field styleField = attr.getClass().getDeclaredField("style");
            styleField.setAccessible(true);
            int styleValue = styleField.getInt(attr);
            java.lang.reflect.Field fForegroundField = attr.getClass().getDeclaredField("F_FOREGROUND");
            fForegroundField.setAccessible(true);
            int F_FOREGROUND = fForegroundField.getInt(attr);
            java.lang.reflect.Field fgColorExpField = attr.getClass().getDeclaredField("FG_COLOR_EXP");
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
        List<StyleTextPair> currentLineSegments = new ArrayList<>();
        int currentLineWidth = 0;
        for (StyleTextPair segment : segments) {
            String text = segment.text;
            Style style = segment.style;
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
                currentLineSegments.add(new StyleTextPair(style, null, substring));
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
        for (StyleTextPair segment : segments) {
            Text styledText = Text.literal(segment.text).setStyle(segment.style);
            lineText.append(styledText);
            plainTextBuilder.append(segment.text);
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

    private int getTotalLines() {
        synchronized (wrappedLinesCache) {
            return wrappedLinesCache.size();
        }
    }

    int getInputFieldHeight() {
        return minecraftClient.textRenderer.fontHeight - 2;
    }

    int getStatusBarHeight() {
        return minecraftClient.textRenderer.fontHeight + 4;
    }

    private OrderedText[] getStatusBarOrderedTexts(int scaledWidth) {
        if (tmuxStatusLine.isEmpty()) {
            if (terminalInstance.getServerInfo() != null) {
                ServerInfo sInfo = terminalInstance.getServerInfo();
                String serverName = sInfo.name;
                String serverStatus;
                switch (sInfo.state) {
                    case STARTING -> serverStatus = "Starting";
                    case RUNNING -> serverStatus = "Running";
                    case STOPPED -> serverStatus = "Stopped";
                    case CRASHED -> serverStatus = "Crashed";
                    default -> serverStatus = "Unknown";
                }
                boolean connected;
                String hostStatus;
                if (sInfo.remoteHost != null && sInfo.remoteSSHManager != null && sInfo.isRemote) {
                    connected = sInfo.remoteSSHManager.isSSH();
                    hostStatus = connected ? sInfo.remoteHost.name + " - Connected" : sInfo.remoteHost.name + ": Disconnected";
                } else {
                    hostStatus = "Local Host | " + new Date();
                }
                return new OrderedText[]{Text.literal("Remotely - 2.0.0 | " + serverName + " - " + serverStatus).asOrderedText(), Text.literal(hostStatus).asOrderedText()};
            } else return new OrderedText[]{Text.literal("Remotely - 2.0.0 | DevBuild4 15/4/2025").asOrderedText(), Text.literal(new Date().toString()).asOrderedText()};
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
        OrderedText leftOrdered = leftWrapped.isEmpty() ? Text.literal(leftText).asOrderedText() : leftWrapped.get(0).orderedText;
        List<StyleTextPair> rightSegments = parseKeywordsAndHighlight(rightText);
        List<LineText> rightWrapped = wrapStyledText(rightSegments, scaledWidth);
        OrderedText rightOrdered = rightWrapped.isEmpty() ? Text.literal(rightText).asOrderedText() : rightWrapped.get(0).orderedText;
        return new OrderedText[]{leftOrdered, rightOrdered};
    }

    public void appendOutput(String text) {
        text = text.replace("\r", "").replace("\t", "    ");
        synchronized (terminalOutput) {
            terminalOutput.append(text);
        }
        rewrap();
        stickToBottom(3);
        minecraftClient.execute(() -> {
            if (terminalInstance.parentScreen != null) {
                terminalInstance.parentScreen.init();
            }
        });
    }

    private void stickToBottom(int thresholdMultiplayer) {
        int padding = 2;
        int textAreaHeight = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();
        int maxScroll = Math.max(0, getTotalScrollHeight() - textAreaHeight);
        int threshold = (minecraftClient.textRenderer.fontHeight + 2) * thresholdMultiplayer;
        if (targetScrollOffset >= maxScroll - threshold) {
            scrollToBottom();
        }
    }

    public void scroll(int direction, int availableHeight) {
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        int maxScroll = Math.max(0, getTotalScrollHeight() - availableHeight);
        int scrollMultiplier = InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) ? 5 : 1;
        float scrollAmount = lineHeight * scrollMultiplier;
        targetScrollOffset -= direction * scrollAmount;
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
    }

    public void scrollToTop() {
        targetScrollOffset = 0;
        Render.ScrollBar.setPendingOffset(targetScrollOffset);
    }

    public void scrollToBottom() {
        int padding = 2;
        int textAreaHeight = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();
        targetScrollOffset = Math.max(0, getTotalScrollHeight() - textAreaHeight);
        Render.ScrollBar.setPendingOffset(targetScrollOffset);
    }

    public StringBuilder getTerminalOutput() {
        return terminalOutput;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isMouseOverTerminal(mouseX, mouseY)) {
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
            scrollToEdgesTerminal(mouseY);
            return true;
        }
        return false;
    }

    private boolean isMouseOverTerminal(double mouseX, double mouseY) {
        int padding = 2;
        int textAreaX = terminalX + padding;
        int textAreaY = terminalY + padding;
        int textAreaWidth = terminalWidth - 2 * padding;
        int textAreaHeight = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();
        return mouseX >= textAreaX && mouseX <= textAreaX + textAreaWidth &&
                mouseY >= textAreaY && mouseY <= textAreaY + textAreaHeight;
    }

    private void updateSelectionStart(double mouseX, double mouseY) {
        int size;
        synchronized (wrappedLinesCache) {
            size = wrappedLinesCache.size();
        }
        if (size == 0) return;
        int padding = 2;
        int textAreaX = terminalX + padding;
        int textAreaY = terminalY + padding;
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        int firstLine = (int) Math.floor(currentScrollOffset / lineHeight);
        int offsetY = (int) ((mouseY - textAreaY) + (currentScrollOffset % lineHeight));
        int clickedLine = firstLine + offsetY / lineHeight;
        synchronized (wrappedLinesCache) {
            if (clickedLine < 0) clickedLine = 0;
            if (clickedLine >= wrappedLinesCache.size()) clickedLine = wrappedLinesCache.size() - 1;
            String lineText = wrappedLinesCache.get(clickedLine).plainText;
            int relativeX = (int) (mouseX - textAreaX);
            int charIndex = 0;
            int widthSum = 0;
            while (charIndex < lineText.length()) {
                int charWidth = minecraftClient.textRenderer.getWidth(String.valueOf(lineText.charAt(charIndex)));
                if (widthSum + charWidth / 2 >= relativeX) break;
                widthSum += charWidth;
                charIndex++;
            }
            selectionStartLine = clickedLine;
            selectionStartChar = charIndex;
            selectionEndLine = clickedLine;
            selectionEndChar = charIndex;
        }
    }

    private void updateSelectionEnd(double mouseX, double mouseY) {
        int size;
        synchronized (wrappedLinesCache) {
            size = wrappedLinesCache.size();
        }
        if (size == 0) return;
        int padding = 2;
        int textAreaX = terminalX + padding;
        int textAreaY = terminalY + padding;
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        int firstLine = (int) Math.floor(currentScrollOffset / lineHeight);
        int offsetY = (int) ((mouseY - textAreaY) + (currentScrollOffset % lineHeight));
        int clickedLine = firstLine + offsetY / lineHeight;
        synchronized (wrappedLinesCache) {
            if (clickedLine < 0) clickedLine = 0;
            if (clickedLine >= wrappedLinesCache.size()) clickedLine = wrappedLinesCache.size() - 1;
            String lineText = wrappedLinesCache.get(clickedLine).plainText;
            int relativeX = (int) (mouseX - textAreaX);
            int charIndex = 0;
            int widthSum = 0;
            while (charIndex < lineText.length()) {
                int charWidth = minecraftClient.textRenderer.getWidth(String.valueOf(lineText.charAt(charIndex)));
                if (widthSum + charWidth / 2 >= relativeX) break;
                widthSum += charWidth;
                charIndex++;
            }
            selectionEndLine = clickedLine;
            selectionEndChar = charIndex;
        }
    }

    int getTotalScrollHeight() {
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        return (getTotalLines() * lineHeight);
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void clearOutput() {
        synchronized (terminalOutput) {
            terminalOutput.setLength(0);
        }
        synchronized (wrappedLinesCache) {
            wrappedLinesCache.clear();
        }
        minecraftClient.execute(() -> {
            if (terminalInstance.parentScreen != null) {
                terminalInstance.parentScreen.init();
            }
        });
    }

    public String getTerminalContext() {
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

    private static class StyleTextPair {
        final Style style;
        final TextColor backgroundColor;
        final String text;

        StyleTextPair(Style style, TextColor backgroundColor, String text) {
            this.style = style;
            this.backgroundColor = backgroundColor;
            this.text = text;
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
        int selectionXStart = x;
        for (int i = 0; i < selectionStart; i++) {
            selectionXStart += minecraftClient.textRenderer.getWidth(String.valueOf(lineText.charAt(i)));
        }
        int selectionWidth = 0;
        for (int i = selectionStart; i < selectionEnd; i++) {
            selectionWidth += minecraftClient.textRenderer.getWidth(String.valueOf(lineText.charAt(i)));
        }
        int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
        context.fill(selectionXStart, yPosition, selectionXStart + selectionWidth, yPosition + lineHeight, globalSelectionColor);
    }

    private void scrollToEdgesTerminal(double mouseY) {
        int padding = 2;
        int textAreaY = terminalY + padding;
        int textAreaHeight = terminalHeight - 2 * padding - getInputFieldHeight() - getStatusBarHeight();
        int maxScroll = Math.max(0, getTotalScrollHeight() - textAreaHeight);
        double speedFactor = 0.1;
        double minDiff = 5.0;
        if (mouseY < textAreaY) {
            double diff = textAreaY - mouseY;
            diff = Math.max(diff, minDiff);
            int scrollAmount = (int) (diff * speedFactor);
            targetScrollOffset = Math.max(0, targetScrollOffset - scrollAmount);
        } else if (mouseY > textAreaY + textAreaHeight) {
            double diff = mouseY - (textAreaY + textAreaHeight);
            diff = Math.max(diff, minDiff);
            int scrollAmount = (int) (diff * speedFactor);
            targetScrollOffset = Math.min(maxScroll, targetScrollOffset + scrollAmount);
        }
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
            String lineText;
            synchronized (wrappedLinesCache) {
                if (i < 0 || i >= wrappedLinesCache.size()) continue;
                lineText = wrappedLinesCache.get(i).plainText;
            }
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
}
