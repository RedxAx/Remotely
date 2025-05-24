package redxax.oxy.remotely.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class MarkdownRenderer {
    private final String rawMarkdown;
    private final List<MarkdownElement> elements = new ArrayList<>();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private float height = 0;
    private int lastWidth = 0;
    private boolean parsed = false;

    private static final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    // Limit concurrent image loads to 4
    private static final java.util.concurrent.ExecutorService imageLoaderPool = java.util.concurrent.Executors.newFixedThreadPool(4);

    private static final int HEADER_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xDDDDDD;
    private static final int LINK_COLOR = 0x5C9CFF;
    private static final int CODE_BACKGROUND = 0x22222266;
    private static final int TABLE_HEADER_BG = 0x44444466;
    private static final int TABLE_BORDER = 0x666666FF;

    private static final int DIV_BACKGROUND = 0x33333366;
    private static final int DIV_BORDER = 0x55555555;
    private static final int BLOCKQUOTE_BG = 0x33336633;
    private static final int BLOCKQUOTE_BORDER = 0x666699FF;

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*?)\\]\\(([^)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+?)\\*\\*|__([^_]+?)__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+?)\\*(?!\\*)|(?<!_)_([^_]+?)_(?!_)");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("^```(?:([a-zA-Z0-9+-]+))?\\s*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*)[*+-]\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)\\d+\\.\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|(.+)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|(?::?-+:?\\|)+\\s*$");
    private static final Pattern HTML_TAG = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)>(?:(.*?)</\\1>)?", Pattern.DOTALL);

    public MarkdownRenderer(String markdown) {
        this.rawMarkdown = markdown;
    }

    private void parse(int availableWidth) {
        if (parsed && availableWidth == lastWidth) {
            return;
        }

        elements.clear();
        height = 0;
        lastWidth = availableWidth;

        if (rawMarkdown == null || rawMarkdown.isEmpty()) {
            parsed = true;
            return;
        }

        String[] lines = rawMarkdown.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int y = 0;
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            if (line.trim().isEmpty()) {
                y += 5;
                i++;
                continue;
            }

            Matcher headerMatcher = HEADER_PATTERN.matcher(line.trim());
            if (headerMatcher.matches()) {
                String headerMarker = headerMatcher.group(1);
                String headerText = headerMatcher.group(2);
                int level = headerMarker.length();
                HeaderElement header = new HeaderElement(headerText, level);
                header.y = y;
                elements.add(header);
                y += header.getHeight(client.textRenderer) + 5;
                i++;
                continue;
            }

            Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(line.trim());
            if (codeBlockMatcher.matches()) {
                String language = codeBlockMatcher.group(1);
                StringBuilder codeContent = new StringBuilder();
                i++;

                while (i < lines.length) {
                    if (lines[i].trim().equals("```")) {
                        break;
                    }
                    if (codeContent.length() > 0) {
                        codeContent.append("\n");
                    }
                    codeContent.append(lines[i]);
                    i++;
                }

                CodeBlockElement codeBlock = new CodeBlockElement(codeContent.toString(), language);
                codeBlock.y = y;
                codeBlock.calculate(client.textRenderer, availableWidth);
                elements.add(codeBlock);
                y += codeBlock.height + 10;
                i++;
                continue;
            }

            if (TABLE_ROW_PATTERN.matcher(line.trim()).matches()) {
                StringBuilder tableContent = new StringBuilder();
                int tableStart = i;

                while (i < lines.length && (TABLE_ROW_PATTERN.matcher(lines[i].trim()).matches() || TABLE_SEPARATOR.matcher(lines[i].trim()).matches())) {
                    if (tableContent.length() > 0) {
                        tableContent.append("\n");
                    }
                    tableContent.append(lines[i]);
                    i++;
                }

                TableElement table = parseTable(tableContent.toString());
                table.y = y;
                table.calculate(client.textRenderer, availableWidth);
                elements.add(table);
                y += table.height + 10;
                continue;
            }

            Matcher listMatcher = LIST_PATTERN.matcher(line);
            Matcher orderedListMatcher = ORDERED_LIST_PATTERN.matcher(line);

            if (listMatcher.matches() || orderedListMatcher.matches()) {
                StringBuilder listContent = new StringBuilder();
                boolean isOrdered = orderedListMatcher.matches();

                while (i < lines.length) {
                    String currentLine = lines[i];
                    Matcher currentListMatcher = isOrdered ? ORDERED_LIST_PATTERN.matcher(currentLine) : LIST_PATTERN.matcher(currentLine);

                    if (!currentListMatcher.matches() && !currentLine.trim().isEmpty()) {
                        break;
                    }

                    if (currentLine.trim().isEmpty()) {
                        i++;
                        continue;
                    }

                    if (listContent.length() > 0) {
                        listContent.append("\n");
                    }
                    listContent.append(currentLine);
                    i++;
                }

                ListElement list = parseList(listContent.toString(), isOrdered);
                list.y = y;
                list.calculate(client.textRenderer, availableWidth);
                elements.add(list);
                y += list.height + 10;
                continue;
            }

            Matcher imageMatcher = IMAGE_PATTERN.matcher(line.trim());
            if (imageMatcher.find() && imageMatcher.start() == 0) {
                String alt = imageMatcher.group(1);
                String url = imageMatcher.group(2);
                ImageElement imageElement = new ImageElement(url, alt);
                imageElement.y = y;
                imageElement.calculate(client.textRenderer, availableWidth);
                elements.add(imageElement);
                y += imageElement.height + 15;
                i++;
                continue;
            }

            Matcher htmlMatcher = HTML_TAG.matcher(line.trim());
            if (htmlMatcher.matches()) {
                String tag = htmlMatcher.group(1);
                String attributes = htmlMatcher.group(2);
                String content = htmlMatcher.group(3);
                HtmlElement htmlElement = new HtmlElement(tag, attributes, content);
                htmlElement.y = y;
                htmlElement.calculate(client.textRenderer, availableWidth);
                elements.add(htmlElement);
                y += (int) (htmlElement.height + 10);
                i++;
                continue;
            }

            StringBuilder paragraphContent = new StringBuilder();
            while (i < lines.length && !lines[i].trim().isEmpty()) {
                if (HEADER_PATTERN.matcher(lines[i].trim()).matches() ||
                        CODE_BLOCK_PATTERN.matcher(lines[i].trim()).matches() ||
                        LIST_PATTERN.matcher(lines[i]).matches() ||
                        ORDERED_LIST_PATTERN.matcher(lines[i]).matches() ||
                        TABLE_ROW_PATTERN.matcher(lines[i].trim()).matches() ||
                        IMAGE_PATTERN.matcher(lines[i].trim()).find() ||
                        HTML_TAG.matcher(lines[i].trim()).matches()) {
                    break;
                }

                if (paragraphContent.length() > 0) {
                    paragraphContent.append(" ");
                }
                paragraphContent.append(lines[i].trim());
                i++;
            }

            if (paragraphContent.length() > 0) {
                TextElement text = new TextElement(paragraphContent.toString());
                text.y = y;
                text.calculate(client.textRenderer, availableWidth);
                elements.add(text);
                y += text.height + 8;
            }
        }

        height = y;
        parsed = true;
    }

    private TableElement parseTable(String tableMarkdown) {
        String[] lines = tableMarkdown.split("\\n");
        List<String[]> rows = new ArrayList<>();
        int headerRowIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (TABLE_SEPARATOR.matcher(line).matches()) {
                if (i > 0) {
                    headerRowIndex = i - 1;
                }
                continue;
            }

            if (line.startsWith("|")) {
                line = line.substring(1);
            }
            if (line.endsWith("|")) {
                line = line.substring(0, line.length() - 1);
            }

            String[] cells = line.split("\\|");
            for (int j = 0; j < cells.length; j++) {
                cells[j] = cells[j].trim();
            }

            rows.add(cells);
        }

        if (rows.isEmpty()) {
            return new TableElement(new String[0][0], -1);
        }

        String[][] tableData = rows.toArray(new String[0][0]);
        return new TableElement(tableData, headerRowIndex);
    }

    private ListElement parseList(String listMarkdown, boolean ordered) {
        String[] lines = listMarkdown.split("\\n");
        List<ListItem> items = new ArrayList<>();
        int index = 1;

        Pattern itemPattern = ordered ? ORDERED_LIST_PATTERN : LIST_PATTERN;

        for (String line : lines) {
            Matcher matcher = itemPattern.matcher(line);
            if (matcher.find()) {
                String indent = matcher.group(1);
                String content = matcher.group(2);
                int depth = indent.length() / 2;
                ListItem item = new ListItem(content, depth);
                if (ordered) {
                    item.index = index++;
                }
                items.add(item);
            }
        }

        return new ListElement(items, ordered);
    }

    public boolean draw(int x, int y, int width, float mouseX, float mouseY, DrawContext context) {
        parse(width);

        for (MarkdownElement element : elements) {
            element.render(x, y + element.y, width, context, client.textRenderer);
        }

        return false;
    }

    public boolean onClick(int x, int y, int button, float mouseX, float mouseY) {
        for (MarkdownElement element : elements) {
            if (element.onClick(x, y + element.y, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean onMouseClicked(int baseX, int baseY, float mouseX, float mouseY, int button) {
        return onClick(baseX, baseY, button, mouseX, mouseY);
    }

    public float getHeight() {
        return height;
    }

    private static abstract class MarkdownElement {
        int y;
        float height;

        abstract void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer);

        boolean onClick(int x, int y, float mouseX, float mouseY) {
            return false;
        }
    }

    private static class HeaderElement extends MarkdownElement {
        private final String text;
        private final int level;

        public HeaderElement(String text, int level) {
            this.text = text;
            this.level = level;
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            float scale = getScale();
            int headerY = y;

            context.getMatrices().push();
            context.getMatrices().scale(scale, scale, 1.0f);

            float scaledX = x / scale;
            float scaledY = headerY / scale;

            context.drawText(
                    textRenderer,
                    Text.literal(text).formatted(Formatting.BOLD),
                    (int)scaledX,
                    (int)scaledY,
                    HEADER_COLOR,
                    Config.shadow
            );

            context.getMatrices().pop();

            if (level <= 2) {
                context.fill(x, (int) (y + getHeight(textRenderer) - 2), x + width, (int) (y + getHeight(textRenderer)), HEADER_COLOR);
            }
        }

        private float getScale() {
            switch (level) {
                case 1: return 1.8f;
                case 2: return 1.5f;
                case 3: return 1.3f;
                case 4: return 1.2f;
                case 5: return 1.1f;
                default: return 1.0f;
            }
        }

        float getHeight(TextRenderer textRenderer) {
            return textRenderer.fontHeight * getScale() + (level <= 2 ? 6 : 3);
        }
    }

    private static class TextElement extends MarkdownElement {
        private final String originalText;
        private final List<TextSpan> spans = new ArrayList<>();

        public TextElement(String text) {
            this.originalText = text;
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            spans.clear();
            processFormattedText(textRenderer, originalText, availableWidth);

            if (spans.isEmpty()) {
                height = textRenderer.fontHeight;
                return;
            }

            int maxY = 0;
            for (TextSpan span : spans) {
                maxY = Math.max(maxY, span.y + textRenderer.fontHeight);
            }
            height = maxY;
        }

        private void processFormattedText(TextRenderer textRenderer, String text, int availableWidth) {
            if (text == null) text = "";
            List<FormatSegment> segments = new ArrayList<>();
            findAllFormattingSegments(text, segments);

            if (segments.isEmpty()) {
                TextSpan span = new TextSpan(text, 0, null, SpanType.TEXT);
                spans.add(span);
                layoutSpans(textRenderer, availableWidth);
                return;
            }

            segments.sort(Comparator.comparingInt(s -> s.start));

            int lastEnd = 0;
            for (FormatSegment segment : segments) {
                if (segment.start > lastEnd) {
                    String beforeText = text.substring(lastEnd, segment.start);
                    if (!beforeText.isEmpty()) {
                        TextSpan span = new TextSpan(beforeText, lastEnd, null, SpanType.TEXT);
                        spans.add(span);
                    }
                }

                TextSpan span = new TextSpan(segment.content, segment.start, segment.url, segment.type);
                spans.add(span);
                lastEnd = segment.end;
            }

            if (lastEnd < text.length()) {
                String afterText = text.substring(lastEnd);
                if (!afterText.isEmpty()) {
                    TextSpan span = new TextSpan(afterText, lastEnd, null, SpanType.TEXT);
                    spans.add(span);
                }
            }

            layoutSpans(textRenderer, availableWidth);
        }

        private void findAllFormattingSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            findLinkSegments(text, segments);
            findImageSegments(text, segments);
            findCodeSegments(text, segments);
            findBoldSegments(text, segments);
            findItalicSegments(text, segments);
        }

        private void findLinkSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            Matcher matcher = LINK_PATTERN.matcher(text);
            while (matcher.find()) {
                if (!isInsideExistingSegment(matcher.start(), matcher.end(), segments)) {
                    segments.add(new FormatSegment(
                            matcher.start(), matcher.end(),
                            matcher.group(1), matcher.group(2), SpanType.LINK
                    ));
                }
            }
        }

        private void findImageSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            Matcher matcher = IMAGE_PATTERN.matcher(text);
            while (matcher.find()) {
                if (!isInsideExistingSegment(matcher.start(), matcher.end(), segments)) {
                    segments.add(new FormatSegment(
                            matcher.start(), matcher.end(),
                            "[" + matcher.group(1) + "]", matcher.group(2), SpanType.IMAGE
                    ));
                }
            }
        }

        private void findCodeSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            Matcher matcher = CODE_PATTERN.matcher(text);
            while (matcher.find()) {
                if (!isInsideExistingSegment(matcher.start(), matcher.end(), segments)) {
                    segments.add(new FormatSegment(
                            matcher.start(), matcher.end(),
                            matcher.group(1), null, SpanType.CODE
                    ));
                }
            }
        }

        private void findBoldSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            Matcher matcher = BOLD_PATTERN.matcher(text);
            while (matcher.find()) {
                if (!isInsideExistingSegment(matcher.start(), matcher.end(), segments)) {
                    String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    segments.add(new FormatSegment(
                            matcher.start(), matcher.end(),
                            content, null, SpanType.BOLD
                    ));
                }
            }
        }

        private void findItalicSegments(String text, List<FormatSegment> segments) {
            if (text == null) return;
            Matcher matcher = ITALIC_PATTERN.matcher(text);
            while (matcher.find()) {
                if (!isInsideExistingSegment(matcher.start(), matcher.end(), segments)) {
                    String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    segments.add(new FormatSegment(
                            matcher.start(), matcher.end(),
                            content, null, SpanType.ITALIC
                    ));
                }
            }
        }

        private boolean isInsideExistingSegment(int start, int end, List<FormatSegment> segments) {
            for (FormatSegment segment : segments) {
                if (start >= segment.start && end <= segment.end) {
                    return true;
                }
            }
            return false;
        }

        private void layoutSpans(TextRenderer textRenderer, int availableWidth) {
            int currentX = 0;
            int currentY = 0;
            int lineHeight = textRenderer.fontHeight + 2;

            for (TextSpan span : spans) {
                String[] words = span.text.split("\\s+");

                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    if (i < words.length - 1) {
                        word += " ";
                    }

                    int wordWidth = getTextWidth(textRenderer, word, span.type);

                    if (currentX + wordWidth > availableWidth && currentX > 0) {
                        currentY += lineHeight;
                        currentX = 0;
                    }

                    TextSpan wordSpan = new TextSpan(word, span.start, span.url, span.type);
                    wordSpan.x = currentX;
                    wordSpan.y = currentY;

                    currentX += wordWidth;
                }
            }

            spans.clear();
            currentX = 0;
            currentY = 0;

            for (TextSpan originalSpan : spans) {
                String[] words = originalSpan.text.split("\\s+");

                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    if (i < words.length - 1) {
                        word += " ";
                    }

                    int wordWidth = getTextWidth(textRenderer, word, originalSpan.type);

                    if (currentX + wordWidth > availableWidth && currentX > 0) {
                        currentY += lineHeight;
                        currentX = 0;
                    }

                    TextSpan wordSpan = new TextSpan(word, originalSpan.start, originalSpan.url, originalSpan.type);
                    wordSpan.x = currentX;
                    wordSpan.y = currentY;
                    spans.add(wordSpan);

                    currentX += wordWidth;
                }
            }
        }

        private int getTextWidth(TextRenderer textRenderer, String text, SpanType type) {
            int baseWidth = textRenderer.getWidth(text);
            if (type == SpanType.BOLD) {
                return (int)(baseWidth * 1.1f);
            }
            return baseWidth;
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            for (TextSpan span : spans) {
                int spanX = x + span.x;
                int spanY = y + span.y;

                switch (span.type) {
                    case LINK:
                        context.drawText(textRenderer, Text.literal(span.text).formatted(Formatting.UNDERLINE), spanX, spanY, LINK_COLOR, Config.shadow);
                        break;
                    case BOLD:
                        context.drawText(textRenderer, Text.literal(span.text).formatted(Formatting.BOLD), spanX, spanY, TEXT_COLOR, Config.shadow);
                        break;
                    case ITALIC:
                        context.drawText(textRenderer, Text.literal(span.text).formatted(Formatting.ITALIC), spanX, spanY, TEXT_COLOR, Config.shadow);
                        break;
                    case CODE:
                        int codeWidth = textRenderer.getWidth(span.text);
                        context.fill(spanX - 2, spanY - 1, spanX + codeWidth + 2, spanY + textRenderer.fontHeight + 1, CODE_BACKGROUND);
                        context.drawText(textRenderer, Text.literal(span.text), spanX, spanY, 0xFFFFFF, Config.shadow);
                        break;
                    case IMAGE:
                        context.drawText(textRenderer, Text.literal(span.text).formatted(Formatting.ITALIC), spanX, spanY, 0xAAAAAA, Config.shadow);
                        break;
                    default:
                        context.drawText(textRenderer, Text.literal(span.text), spanX, spanY, TEXT_COLOR, Config.shadow);
                }
            }
        }

        @Override
        boolean onClick(int x, int y, float mouseX, float mouseY) {
            for (TextSpan span : spans) {
                if (span.type == SpanType.LINK && span.url != null &&
                        mouseX >= x + span.x &&
                        mouseX <= x + span.x + getTextWidth(MinecraftClient.getInstance().textRenderer, span.text, span.type) &&
                        mouseY >= y + span.y &&
                        mouseY <= y + span.y + MinecraftClient.getInstance().textRenderer.fontHeight) {

                    try {
                        String url = span.url;
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", url);
                        pb.start();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            return false;
        }
    }

    private static class FormatSegment {
        int start;
        int end;
        String content;
        String url;
        SpanType type;

        FormatSegment(int start, int end, String content, String url, SpanType type) {
            this.start = start;
            this.end = end;
            this.content = content;
            this.url = url;
            this.type = type;
        }
    }

    private static class CodeBlockElement extends MarkdownElement {
        private final String code;
        private final String language;
        private final List<String> lines = new ArrayList<>();

        public CodeBlockElement(String code, String language) {
            this.code = code;
            this.language = language;
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            lines.clear();
            if (code.isEmpty()) {
                height = 20;
                return;
            }

            Collections.addAll(lines, code.split("\\n"));
            int languageHeaderHeight = (language != null && !language.isEmpty()) ? textRenderer.fontHeight + 6 : 0;
            height = lines.size() * (textRenderer.fontHeight + 1) + 15 + languageHeaderHeight;
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            int borderColor = 0x55555555;
            context.fill(x, y, x + width, (int) (y + height), CODE_BACKGROUND);
            context.fill(x, y, x + width, y + 1, borderColor);
            context.fill(x, y, x + 1, (int) (y + height), borderColor);
            context.fill(x, (int) (y + height - 1), x + width, (int) (y + height), borderColor);
            context.fill(x + width - 1, y, x + width, (int) (y + height), borderColor);

            int startY = y + 8;
            if (language != null && !language.isEmpty()) {
                String lang = language.toUpperCase();
                int langWidth = textRenderer.getWidth(lang);
                context.fill(x + 8, y + 3, x + langWidth + 16, y + textRenderer.fontHeight + 7, 0x88333333);
                context.drawText(textRenderer, Text.literal(lang), x + 12, y + 5, 0xCCCCCC, Config.shadow);
                startY = y + textRenderer.fontHeight + 12;
            }

            int lineY = startY;
            for (String line : lines) {
                context.drawText(textRenderer, Text.literal(line), x + 8, lineY, 0xFFFFFF, Config.shadow);
                lineY += textRenderer.fontHeight + 1;
            }
        }
    }

    private static class TableElement extends MarkdownElement {
        private final String[][] data;
        private final int headerRowIndex;
        private int[] columnWidths;
        private Map<String, List<String>> wrappedTextCache = new HashMap<>();
        private int[] rowHeights;

        public TableElement(String[][] data, int headerRowIndex) {
            this.data = data;
            this.headerRowIndex = headerRowIndex;
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            if (data.length == 0) {
                height = 0;
                return;
            }

            int columns = 0;
            for (String[] row : data) {
                columns = Math.max(columns, row.length);
            }

            if (columns == 0) {
                height = 0;
                return;
            }

            columnWidths = new int[columns];
            Arrays.fill(columnWidths, 0);

            for (String[] row : data) {
                for (int i = 0; i < row.length && i < columns; i++) {
                    columnWidths[i] = Math.max(columnWidths[i], textRenderer.getWidth(row[i]) + 10);
                }
            }

            int totalWidth = Arrays.stream(columnWidths).sum() + (columns + 1) * 2;
            if (totalWidth > availableWidth) {
                float ratio = (float) (availableWidth - (columns + 1) * 2) / Arrays.stream(columnWidths).sum();
                for (int i = 0; i < columnWidths.length; i++) {
                    columnWidths[i] = Math.max(50, (int) (columnWidths[i] * ratio));
                }
            }

            rowHeights = new int[data.length];
            wrappedTextCache.clear();

            for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
                String[] row = data[rowIndex];
                int maxCellHeight = textRenderer.fontHeight + 8;

                for (int colIndex = 0; colIndex < Math.min(row.length, columnWidths.length); colIndex++) {
                    String cellText = row[colIndex];
                    List<String> wrappedText = wrapText(cellText, textRenderer, columnWidths[colIndex] - 10);
                    wrappedTextCache.put(rowIndex + "_" + colIndex, wrappedText);
                    maxCellHeight = Math.max(maxCellHeight, wrappedText.size() * (textRenderer.fontHeight + 1) + 8);
                }

                rowHeights[rowIndex] = maxCellHeight;
            }

            height = Arrays.stream(rowHeights).sum() + data.length + 1;
        }

        private List<String> wrapText(String text, TextRenderer renderer, int maxWidth) {
            if (maxWidth <= 0) {
                return Collections.singletonList(text);
            }

            List<String> lines = new ArrayList<>();
            String[] words = text.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.length() > 0
                        ? currentLine.toString() + " " + word
                        : word;

                if (renderer.getWidth(testLine) <= maxWidth) {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        lines.add(word);
                    }
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }

            return lines.isEmpty() ? Collections.singletonList(text) : lines;
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            if (data.length == 0 || columnWidths == null) return;

            int startX = x;
            int currentY = y;

            context.fill(startX, currentY, startX + Arrays.stream(columnWidths).sum() + columnWidths.length + 1, currentY + 1, TABLE_BORDER);

            for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
                String[] row = data[rowIndex];
                int cellX = startX;
                int rowHeight = rowHeights[rowIndex];

                if (rowIndex == headerRowIndex) {
                    context.fill(cellX, currentY, cellX + Arrays.stream(columnWidths).sum() + columnWidths.length + 1, currentY + rowHeight, TABLE_HEADER_BG);
                }

                context.fill(cellX, currentY, cellX + 1, currentY + rowHeight, TABLE_BORDER);

                for (int colIndex = 0; colIndex < columnWidths.length; colIndex++) {
                    int cellWidth = columnWidths[colIndex];

                    if (colIndex < row.length) {
                        List<String> wrappedLines = wrappedTextCache.get(rowIndex + "_" + colIndex);
                        if (wrappedLines != null) {
                            int lineY = currentY + 4;
                            for (String line : wrappedLines) {
                                context.drawText(
                                        textRenderer,
                                        Text.literal(line),
                                        cellX + 5,
                                        lineY,
                                        rowIndex == headerRowIndex ? HEADER_COLOR : TEXT_COLOR,
                                        Config.shadow
                                );
                                lineY += textRenderer.fontHeight + 1;
                            }
                        }
                    }

                    cellX += cellWidth;
                    context.fill(cellX, currentY, cellX + 1, currentY + rowHeight, TABLE_BORDER);
                }

                currentY += rowHeight;
                context.fill(startX, currentY, cellX, currentY + 1, TABLE_BORDER);
            }
        }
    }

    private static class ListElement extends MarkdownElement {
        private final List<ListItem> items;
        private final boolean ordered;
        private final Map<Integer, List<String>> wrappedTextCache = new HashMap<>();

        public ListElement(List<ListItem> items, boolean ordered) {
            this.items = items;
            this.ordered = ordered;
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            int currentY = 0;
            int lineHeight = textRenderer.fontHeight + 4;
            int indent = 20;
            wrappedTextCache.clear();

            for (int i = 0; i < items.size(); i++) {
                ListItem item = items.get(i);
                String prefix = ordered ? (item.index) + ". " : "• ";
                int prefixWidth = textRenderer.getWidth(prefix);
                int itemIndent = item.depth * indent;

                String text = item.text;
                int availableItemWidth = Math.max(50, availableWidth - (itemIndent + prefixWidth + 10));

                List<String> wrappedLines = wrapText(text, textRenderer, availableItemWidth);
                wrappedTextCache.put(i, wrappedLines);
                item.height = wrappedLines.size() * lineHeight;
                item.y = currentY;

                currentY += item.height + 2;
            }

            height = currentY;
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            int indent = 20;
            int lineHeight = textRenderer.fontHeight + 4;

            for (int i = 0; i < items.size(); i++) {
                ListItem item = items.get(i);
                String prefix = ordered ? item.index + ". " : "• ";
                int itemIndent = item.depth * indent;
                int itemX = x + itemIndent;
                int itemY = y + item.y;

                context.drawText(textRenderer, Text.literal(prefix), itemX, itemY, TEXT_COLOR, Config.shadow);
                int prefixWidth = textRenderer.getWidth(prefix);

                List<String> wrappedLines = wrappedTextCache.get(i);
                if (wrappedLines != null) {
                    int lineY = itemY;
                    for (String line : wrappedLines) {
                        int lineX = itemX + prefixWidth;
                        context.drawText(textRenderer, Text.literal(line), lineX, lineY, TEXT_COLOR, Config.shadow);
                        lineY += lineHeight;
                    }
                }
            }
        }

        private List<String> wrapText(String text, TextRenderer renderer, int maxWidth) {
            if (maxWidth <= 0) {
                return Collections.singletonList(text);
            }

            List<String> lines = new ArrayList<>();
            String[] words = text.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.length() > 0
                        ? currentLine.toString() + " " + word
                        : word;

                if (renderer.getWidth(testLine) <= maxWidth) {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        lines.add(word);
                    }
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }

            return lines.isEmpty() ? Collections.singletonList(text) : lines;
        }
    }

    private static class ListItem {
        String text;
        int depth;
        int index;
        int y;
        int height;

        ListItem(String text, int depth) {
            this.text = text;
            this.depth = depth;
            this.index = 1;
        }
    }

    private static class TextSpan {
        String text;
        int start;
        String url;
        SpanType type;
        int x;
        int y;

        TextSpan(String text, int start, String url, SpanType type) {
            this.text = text;
            this.start = start;
            this.url = url;
            this.type = type;
        }
    }

    private enum SpanType {
        TEXT, LINK, BOLD, ITALIC, CODE, IMAGE, HTML_LINK, HTML_BOLD, HTML_ITALIC, HTML_CODE,
        BOLD_ITALIC, LINK_BOLD, LINK_ITALIC, LINK_BOLD_ITALIC
    }

    private static class ImageElement extends MarkdownElement {
        private final String url;
        private final String alt;
        private BufferedImage image;
        private boolean loaded = false;
        private boolean loading = false;
        private boolean failed = false;
        private int maxWidth;
        private Runnable onImageLoaded; // callback for parent
        private static final int TIMEOUT_MS = 4000; // 4 seconds timeout
        private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB limit

        public ImageElement(String url, String alt) {
            this.url = url;
            this.alt = alt;
            this.height = 40; // Smaller initial height for placeholder
        }

        public void setOnImageLoaded(Runnable onImageLoaded) {
            this.onImageLoaded = onImageLoaded;
        }

        private void loadImage() {
            if (loading || loaded) return;
            loading = true;

            // Don't block the main thread for image loading
            imageLoaderPool.submit(() -> {
                try {
                    if (imageCache.containsKey(url)) {
                        image = imageCache.get(url);
                        updateHeightFromImage();
                        loaded = true;
                    } else {
                        try {
                            URL imageUrl = new URL(url);
                            java.net.URLConnection connection = imageUrl.openConnection();
                            connection.setConnectTimeout(TIMEOUT_MS);
                            connection.setReadTimeout(TIMEOUT_MS);
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                            // Check content type before loading
                            String contentType = connection.getContentType();
                            if (contentType != null && !contentType.startsWith("image/")) {
                                failed = true;
                                loaded = true;
                                return;
                            }

                            // Check content length if available
                            int contentLength = connection.getContentLength();
                            if (contentLength > MAX_IMAGE_SIZE && contentLength > 0) {
                                failed = true;
                                loaded = true;
                                return;
                            }

                            try (InputStream in = new java.io.BufferedInputStream(connection.getInputStream())) {
                                image = ImageIO.read(in);
                                if (image != null) {
                                    // Only cache reasonably sized images
                                    if (image.getWidth() * image.getHeight() < 4000000) { // ~2000x2000 max
                                        imageCache.put(url, image);
                                    }
                                    updateHeightFromImage();
                                } else {
                                    failed = true;
                                }
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            failed = true;
                        } catch (javax.imageio.IIOException e) {
                            failed = true;
                        } catch (Exception e) {
                            failed = true;
                        }
                        loaded = true;
                    }
                } catch (Exception e) {
                    failed = true;
                    loaded = true;
                } finally {
                    loading = false;
                    // Notify parent when the image is loaded or failed
                    if (onImageLoaded != null) {
                        try {
                            onImageLoaded.run();
                        } catch (Exception e) {
                            // Ignore callback errors
                        }
                    }
                }
            });
        }

        private void updateHeightFromImage() {
            if (image == null) return;
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            if (maxWidth > 0 && originalWidth > maxWidth) {
                float ratio = (float) maxWidth / originalWidth;
                height = (int) (originalHeight * ratio) + 10;
            } else {
                height = originalHeight + 10;
            }
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            this.maxWidth = availableWidth - 20;
            if (!loaded && !loading) {
                loadImage();
            } else if (loaded && image != null) {
                updateHeightFromImage();
            } else if (loaded && image == null) {
                // Failed to load or not loaded yet
                height = textRenderer.fontHeight + 20;
            }
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            if (loaded && image != null) {
                int imgWidth = image.getWidth();
                if (imgWidth > maxWidth) {
                    float ratio = (float) maxWidth / imgWidth;
                    int displayWidth = maxWidth;
                    int displayHeight = (int) (image.getHeight() * ratio);

                    ImageUtil.drawBufferedImage(context, image, x + 10, y + 5, displayWidth, displayHeight);
                } else {
                    ImageUtil.drawBufferedImage(context, image, x + 10, y + 5, image.getWidth(), image.getHeight());
                }
            } else {
                // Show loading or error state
                String statusText;
                if (loading) {
                    statusText = "Loading image: " + (alt != null && !alt.isEmpty() ? alt : url);
                } else if (failed) {
                    statusText = "Failed to load image" + (alt != null && !alt.isEmpty() ? ": " + alt : "");
                } else {
                    statusText = "Waiting to load image" + (alt != null && !alt.isEmpty() ? ": " + alt : "");
                }

                context.fill(x + 10, y + 5, x + Math.min(300, width - 20), y + textRenderer.fontHeight + 10, 0x22222266);
                context.drawText(textRenderer, Text.literal(statusText), x + 15, y + 8, 0xAAAAAA, Config.shadow);
            }
        }
    }

    private static class HtmlElement extends MarkdownElement {
        private final String tag;
        private final String attributes;
        private final String content;
        private final Map<String, String> parsedAttributes = new HashMap<>();
        private final List<MarkdownElement> childElements = new ArrayList<>();

        public HtmlElement(String tag, String attributes, String content) {
            this.tag = tag.toLowerCase();
            this.attributes = attributes;
            this.content = content;

            parseAttributes();
        }

        private void parseAttributes() {
            if (attributes == null || attributes.isEmpty()) return;

            Pattern attrPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9\\-_]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");
            Matcher m = attrPattern.matcher(attributes);

            while (m.find()) {
                String name = m.group(1).toLowerCase();
                String value = m.group(2);
                if (value == null) value = m.group(3);
                if (value == null) value = m.group(4);
                if (value == null) value = "";

                parsedAttributes.put(name, value);
            }
        }

        void calculate(TextRenderer textRenderer, int availableWidth) {
            childElements.clear();

            switch (tag) {
                case "div":
                case "blockquote":
                case "section":
                case "article":
                    if (content != null) {
                        MarkdownRenderer innerRenderer = new MarkdownRenderer(content);
                        innerRenderer.parse(availableWidth - 20);
                        height = innerRenderer.getHeight() + 15;

                        childElements.add(new ContainerContentElement(innerRenderer));
                    }
                    break;

                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    int level = Character.getNumericValue(tag.charAt(1));
                    HeaderElement header = new HeaderElement(content, level);
                    childElements.add(header);
                    height = header.getHeight(textRenderer) + 5;
                    break;

                case "p":
                    TextElement text = new TextElement(content);
                    text.calculate(textRenderer, availableWidth - 20);
                    childElements.add(text);
                    height = text.height + 10;
                    break;

                case "img":
                    String src = parsedAttributes.getOrDefault("src", "");
                    String alt = parsedAttributes.getOrDefault("alt", "");

                    if (!src.isEmpty()) {
                        ImageElement img = new ImageElement(src, alt);
                        img.calculate(textRenderer, availableWidth - 20);
                        childElements.add(img);
                        height = img.height + 15;
                    } else {
                        height = textRenderer.fontHeight + 10;
                    }
                    break;

                case "hr":
                    height = 15;
                    break;

                case "pre":
                    if (content != null) {
                        CodeBlockElement codeBlock = new CodeBlockElement(content, "");
                        codeBlock.calculate(textRenderer, availableWidth - 20);
                        childElements.add(codeBlock);
                        height = codeBlock.height + 10;
                    }
                    break;

                case "code":
                    TextElement codeText = new TextElement("`" + content + "`");
                    codeText.calculate(textRenderer, availableWidth - 20);
                    childElements.add(codeText);
                    height = codeText.height + 5;
                    break;

                case "a":
                    String href = parsedAttributes.getOrDefault("href", "");
                    TextElement linkText = new TextElement("[" + content + "](" + href + ")");
                    linkText.calculate(textRenderer, availableWidth - 20);
                    childElements.add(linkText);
                    height = linkText.height + 5;
                    break;

                default:
                    TextElement defaultText = new TextElement(content != null ? content : "");
                    defaultText.calculate(textRenderer, availableWidth - 20);
                    childElements.add(defaultText);
                    height = defaultText.height + 5;
                    break;
            }
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            int background = 0;
            int border = 0;
            int padding = 8;

            switch (tag) {
                case "div":
                    background = DIV_BACKGROUND;
                    border = DIV_BORDER;
                    break;
                case "blockquote":
                    background = BLOCKQUOTE_BG;
                    border = BLOCKQUOTE_BORDER;

                    context.fill(x, y, x + 4, y + (int)height, border);
                    x += 8;
                    width -= 8;
                    padding = 5;
                    break;
                case "hr":
                    context.fill(x, y + 6, x + width, y + 8, 0x66666666);
                    return;
            }

            if (parsedAttributes.containsKey("style")) {
                String style = parsedAttributes.get("style");

                if (style.contains("background")) {
                    try {
                        int start = style.indexOf("background") + 10;
                        int end = style.indexOf(";", start);
                        if (end == -1) end = style.length();
                        String color = style.substring(start, end).trim();

                        if (color.startsWith("#")) {
                            color = color.substring(1);
                            background = Integer.parseInt(color, 16) | 0x66000000;
                        }
                    } catch (Exception ignored) {}
                }

                if (style.contains("border")) {
                    try {
                        int start = style.indexOf("border") + 6;
                        int end = style.indexOf(";", start);
                        if (end == -1) end = style.length();

                        if (style.substring(start, end).contains("#")) {
                            int colorStart = style.indexOf("#", start) + 1;
                            int colorEnd = Math.min(colorStart + 6, style.length());
                            String color = style.substring(colorStart, colorEnd);
                            border = Integer.parseInt(color, 16) | 0xFF000000;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (background != 0) {
                context.fill(x, y, x + width, y + (int)height, background);
            }

            if (border != 0 && !tag.equals("blockquote")) {
                context.fill(x, y, x + width, y + 1, border);
                context.fill(x, y, x + 1, y + (int)height, border);
                context.fill(x, y + (int)height - 1, x + width, y + (int)height, border);
                context.fill(x + width - 1, y, x + width, y + (int)height, border);
            }

            for (MarkdownElement child : childElements) {
                child.render(x + padding, y + padding, width - padding * 2, context, textRenderer);
            }
        }

        @Override
        boolean onClick(int x, int y, float mouseX, float mouseY) {
            if (tag.equals("a") && parsedAttributes.containsKey("href")) {
                if (mouseX >= x && mouseX <= x + 200 && mouseY >= y && mouseY <= y + height) {
                    try {
                        String href = parsedAttributes.get("href");
                        if (!href.startsWith("http://") && !href.startsWith("https://")) {
                            href = "https://" + href;
                        }
                        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", href);
                        pb.start();
                        return true;
                    } catch (Exception ignored) {}
                }
            }

            for (MarkdownElement child : childElements) {
                if (child.onClick(x, y, mouseX, mouseY)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static class ContainerContentElement extends MarkdownElement {
        private final MarkdownRenderer contentRenderer;

        public ContainerContentElement(MarkdownRenderer contentRenderer) {
            this.contentRenderer = contentRenderer;
            this.height = contentRenderer.getHeight();
        }

        @Override
        void render(int x, int y, int width, DrawContext context, TextRenderer textRenderer) {
            contentRenderer.draw(x, y, width, 0, 0, context);
        }

        @Override
        boolean onClick(int x, int y, float mouseX, float mouseY) {
            return contentRenderer.onClick(x, y, 0, mouseX, mouseY);
        }
    }
}

