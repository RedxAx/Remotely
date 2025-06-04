package redxax.oxy.remotely.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.ImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;

public class MarkdownRenderer {
    private static final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    private static final ExecutorService imageLoader = Executors.newFixedThreadPool(3, r -> {
        Thread thread = new Thread(r, "ImageLoader");
        thread.setDaemon(true);
        return thread;
    });
    private static final int HEADER_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xDDDDDD;
    private static final int LINK_COLOR = 0x5C9CFF;
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]+?)\\*");
    private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("_([^_]+?)_");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("^```([a-zA-Z0-9+-]+)?\\s*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*)[*+-]\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)\\d+\\.\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|(.+)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|(?::?-+:?\\|)+\\s*$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<(\\w+)([^>]*)(?:/>|>([^<]*)</\\1>)", Pattern.DOTALL);
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$");
    private static final Pattern REFERENCE_LINK_PATTERN = Pattern.compile("^\\s*\\[([^]]+)]:\\s*<?([^>\\s]+)>?\\s*(?:\"([^\"]*)\"|'([^']*)')?\\s*$");
    private static final Pattern REFERENCE_USE_PATTERN = Pattern.compile("\\[([^]]+)](?!\\()");
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("<br\\s*/?>");
    private final String rawMarkdown;
    private final List<RenderElement> elements = new ArrayList<>();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private static final Map<String, String> linkReferences = new HashMap<>();
    private int totalHeight = 0;
    private int lastWidth = 0;
    private boolean parsed = false;

    public MarkdownRenderer(String markdown) {
        this.rawMarkdown = markdown != null ? markdown : "";
    }

    public void render(int x, int y, int width, DrawContext context) {
        parse(width);
        for (RenderElement element : elements) {
            element.render(x, y, context, client.textRenderer);
        }
    }

    public boolean handleClick(int x, int y, int width, float mouseX, float mouseY) {
        parse(width);
        for (RenderElement element : elements) {
            if (element.handleClick(x, y, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public int getHeight() {
        parse(lastWidth);
        return totalHeight;
    }

    private void parse(int width) {
        if (parsed && width == lastWidth) return;

        elements.clear();
        totalHeight = 0;
        lastWidth = width;
        parsed = true;
        linkReferences.clear();

        if (rawMarkdown.isEmpty()) return;

        String[] lines = rawMarkdown.replace("\r\n", "\n").split("\n");

        for (String line : lines) {
            Matcher refMatcher = REFERENCE_LINK_PATTERN.matcher(line);
            if (refMatcher.matches()) {
                String name = refMatcher.group(1).toLowerCase();
                String url = refMatcher.group(2);
                linkReferences.put(name, url);
            }
        }

        int currentY = 0;
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            if (line.trim().isEmpty()) {
                currentY += 8;
                i++;
                continue;
            }

            if (REFERENCE_LINK_PATTERN.matcher(line).matches()) {
                i++;
                continue;
            }

            Matcher headerMatcher = HEADER_PATTERN.matcher(line.trim());
            if (headerMatcher.matches()) {
                HeaderElement header = new HeaderElement(headerMatcher.group(2), headerMatcher.group(1).length(), currentY, width);
                elements.add(header);
                currentY += header.getHeight() + (headerMatcher.group(1).length() <= 2 ? 15 : 10);
                i++;
                continue;
            }

            Matcher quoteMatcher = QUOTE_PATTERN.matcher(line);
            if (quoteMatcher.matches()) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && QUOTE_PATTERN.matcher(lines[i]).matches()) {
                    Matcher currentQuote = QUOTE_PATTERN.matcher(lines[i]);
                    if (currentQuote.matches()) {
                        if (quote.length() > 0) quote.append(" ");
                        quote.append(currentQuote.group(1));
                    }
                    i++;
                }

                QuoteElement quoteElement = new QuoteElement(quote.toString(), currentY, width);
                elements.add(quoteElement);
                currentY += quoteElement.getHeight() + 15;
                continue;
            }

            Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(line.trim());
            if (codeBlockMatcher.matches()) {
                StringBuilder code = new StringBuilder();
                String language = codeBlockMatcher.group(1);
                i++;

                while (i < lines.length && !lines[i].trim().equals("```")) {
                    if (code.length() > 0) code.append("\n");
                    code.append(lines[i]);
                    i++;
                }

                CodeBlockElement codeBlock = new CodeBlockElement(code.toString(), language, currentY, width);
                elements.add(codeBlock);
                currentY += codeBlock.getHeight() + 15;
                i++;
                continue;
            }

            if (TABLE_ROW_PATTERN.matcher(line.trim()).matches()) {
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && (TABLE_ROW_PATTERN.matcher(lines[i].trim()).matches() || TABLE_SEPARATOR.matcher(lines[i].trim()).matches())) {
                    tableLines.add(lines[i]);
                    i++;
                }

                TableElement table = new TableElement(tableLines, currentY, width);
                elements.add(table);
                currentY += table.getHeight() + 15;
                continue;
            }

            Matcher listMatcher = LIST_PATTERN.matcher(line);
            Matcher orderedMatcher = ORDERED_LIST_PATTERN.matcher(line);

            if (listMatcher.matches() || orderedMatcher.matches()) {
                List<String> listLines = new ArrayList<>();
                boolean isOrdered = orderedMatcher.matches();

                while (i < lines.length) {
                    String currentLine = lines[i];
                    Matcher currentMatcher = isOrdered ? ORDERED_LIST_PATTERN.matcher(currentLine) : LIST_PATTERN.matcher(currentLine);

                    if (!currentMatcher.matches() && !currentLine.trim().isEmpty()) {
                        break;
                    }

                    if (!currentLine.trim().isEmpty()) {
                        listLines.add(currentLine);
                    }
                    i++;
                }

                ListElement list = new ListElement(listLines, isOrdered, currentY, width);
                elements.add(list);
                currentY += list.getHeight() + 10;
                continue;
            }

            Matcher imageMatcher = IMAGE_PATTERN.matcher(line.trim());
            if (imageMatcher.find() && imageMatcher.start() == 0) {
                ImageElement image = new ImageElement(imageMatcher.group(2), imageMatcher.group(1), currentY, width);
                elements.add(image);
                currentY += image.getHeight() + 15;
                i++;
                continue;
            }

            Matcher htmlMatcher = HTML_TAG_PATTERN.matcher(line.trim());
            if (htmlMatcher.find()) {
                HtmlElement html = new HtmlElement(htmlMatcher.group(1), htmlMatcher.group(2), htmlMatcher.group(3), currentY, width);
                elements.add(html);
                currentY += html.getHeight() + 10;
                i++;
                continue;
            }

            StringBuilder paragraph = new StringBuilder();
            while (i < lines.length && !lines[i].trim().isEmpty()) {
                String currentLine = lines[i].trim();

                if (HEADER_PATTERN.matcher(currentLine).matches() ||
                        CODE_BLOCK_PATTERN.matcher(currentLine).matches() ||
                        LIST_PATTERN.matcher(currentLine).matches() ||
                        ORDERED_LIST_PATTERN.matcher(currentLine).matches() ||
                        TABLE_ROW_PATTERN.matcher(currentLine).matches() ||
                        IMAGE_PATTERN.matcher(currentLine).find() ||
                        HTML_TAG_PATTERN.matcher(currentLine).find() ||
                        QUOTE_PATTERN.matcher(currentLine).matches() ||
                        REFERENCE_LINK_PATTERN.matcher(currentLine).matches()) {
                    break;
                }

                if (paragraph.length() > 0) paragraph.append(" ");
                paragraph.append(currentLine);
                i++;
            }

            if (paragraph.length() > 0) {
                TextElement text = new TextElement(paragraph.toString(), currentY, width);
                elements.add(text);
                currentY += text.getHeight() + 8;
            }
        }

        totalHeight = currentY;
    }

    private abstract static class RenderElement {
        protected int x, y, width, height;
        protected Rectangle bounds;

        public RenderElement(int y, int width) {
            this.y = y;
            this.width = width;
            this.bounds = new Rectangle(0, y, width, 0);
        }

        public abstract void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer);
        public abstract int getHeight();

        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            return false;
        }

        protected boolean isMouseOver(int baseX, int baseY, float mouseX, float mouseY) {
            return mouseX >= baseX + bounds.x && mouseX <= baseX + bounds.x + bounds.width && mouseY >= baseY + bounds.y && mouseY <= baseY + bounds.y + bounds.height;
        }
    }

    private static class HeaderElement extends RenderElement {
        private final String text;
        private final int level;

        public HeaderElement(String text, int level, int y, int width) {
            super(y, width);
            this.text = text;
            this.level = level;
            this.height = getHeight();
            this.bounds.height = height;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            float scale = getScale();
            int renderY = baseY + y;

            context.getMatrices().push();
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawText(textRenderer, Text.literal(text).formatted(Formatting.BOLD), (int)(baseX / scale), (int)(renderY / scale), HEADER_COLOR, Config.shadow);
            context.getMatrices().pop();

            if (level <= 2) {
                int lineY = (int)(renderY + textRenderer.fontHeight * scale + 2);
                context.fill(baseX, lineY, baseX + width, lineY + 1, HEADER_COLOR);
            }
        }

        @Override
        public int getHeight() {
            return (int)(MinecraftClient.getInstance().textRenderer.fontHeight * getScale()) + (level <= 2 ? 8 : 5);
        }

        private float getScale() {
            return switch (level) {
                case 1 -> 1.8f;
                case 2 -> 1.5f;
                case 3 -> 1.3f;
                case 4 -> 1.2f;
                case 5 -> 1.1f;
                default -> 1.0f;
            };
        }
    }

    private static class TextElement extends RenderElement {
        private final String text;
        private final List<TextFragment> fragments = new ArrayList<>();
        private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s)]+)");

        public TextElement(String text, int y, int width) {
            super(y, width);
            this.text = text;
            parseText();
            calculateLayout();
        }

        private void parseText() {
            fragments.clear();
            String remaining = text;
            int offset = 0;

            while (!remaining.isEmpty()) {
                int nextSpecial = findNextSpecialPosition(remaining);

                if (nextSpecial == -1) {
                    fragments.add(new TextFragment(remaining, offset, FragmentType.TEXT, null));
                    break;
                }

                if (nextSpecial > 0) {
                    String beforeText = remaining.substring(0, nextSpecial);
                    fragments.add(new TextFragment(beforeText, offset, FragmentType.TEXT, null));
                }

                FragmentResult result = parseSpecialAt(remaining, nextSpecial);
                if (result != null) {
                    fragments.add(new TextFragment(result.text, offset + nextSpecial, result.type, result.url));
                    remaining = remaining.substring(nextSpecial + result.consumed);
                    offset += nextSpecial + result.consumed;
                } else {
                    fragments.add(new TextFragment(remaining.substring(nextSpecial, nextSpecial + 1), offset + nextSpecial, FragmentType.TEXT, null));
                    remaining = remaining.substring(nextSpecial + 1);
                    offset += nextSpecial + 1;
                }
            }
        }

        private int findNextSpecialPosition(String text) {
            int minPos = Integer.MAX_VALUE;

            Matcher linkMatcher = LINK_PATTERN.matcher(text);
            if (linkMatcher.find()) {
                minPos = linkMatcher.start();
            }

            Matcher urlMatcher = URL_PATTERN.matcher(text);
            if (urlMatcher.find()) {
                minPos = Math.min(minPos, urlMatcher.start());
            }

            Matcher imageMatcher = IMAGE_PATTERN.matcher(text);
            if (imageMatcher.find()) {
                minPos = Math.min(minPos, imageMatcher.start());
            }

            Matcher boldMatcher = BOLD_PATTERN.matcher(text);
            if (boldMatcher.find()) {
                minPos = Math.min(minPos, boldMatcher.start());
            }

            Matcher italicMatcher = ITALIC_PATTERN.matcher(text);
            if (italicMatcher.find()) {
                minPos = Math.min(minPos, italicMatcher.start());
            }

            Matcher italicUnderscoreMatcher = ITALIC_UNDERSCORE_PATTERN.matcher(text);
            if (italicUnderscoreMatcher.find()) {
                minPos = Math.min(minPos, italicUnderscoreMatcher.start());
            }

            Matcher codeMatcher = CODE_PATTERN.matcher(text);
            if (codeMatcher.find()) {
                minPos = Math.min(minPos, codeMatcher.start());
            }

            Matcher refUseMatcher = REFERENCE_USE_PATTERN.matcher(text);
            if (refUseMatcher.find()) {
                minPos = Math.min(minPos, refUseMatcher.start());
            }

            return minPos == Integer.MAX_VALUE ? -1 : minPos;
        }

        private FragmentResult parseSpecialAt(String text, int position) {
            String fromPosition = text.substring(position);

            Matcher linkMatcher = LINK_PATTERN.matcher(fromPosition);
            if (linkMatcher.find() && linkMatcher.start() == 0) {
                return new FragmentResult(linkMatcher.group(1), FragmentType.LINK, linkMatcher.group(2), linkMatcher.group().length());
            }

            Matcher urlMatcher = URL_PATTERN.matcher(fromPosition);
            if (urlMatcher.find() && urlMatcher.start() == 0) {
                String url = urlMatcher.group(1);
                return new FragmentResult(url, FragmentType.LINK, url, url.length());
            }

            Matcher imageMatcher = IMAGE_PATTERN.matcher(fromPosition);
            if (imageMatcher.find() && imageMatcher.start() == 0) {
                return new FragmentResult("[IMG: " + imageMatcher.group(1) + "]", FragmentType.IMAGE, imageMatcher.group(2), imageMatcher.group().length());
            }

            Matcher boldMatcher = BOLD_PATTERN.matcher(fromPosition);
            if (boldMatcher.find() && boldMatcher.start() == 0) {
                return new FragmentResult(boldMatcher.group(1), FragmentType.BOLD, null, boldMatcher.group().length());
            }

            Matcher italicMatcher = ITALIC_PATTERN.matcher(fromPosition);
            if (italicMatcher.find() && italicMatcher.start() == 0) {
                return new FragmentResult(italicMatcher.group(1), FragmentType.ITALIC, null, italicMatcher.group().length());
            }

            Matcher italicUnderscoreMatcher = ITALIC_UNDERSCORE_PATTERN.matcher(fromPosition);
            if (italicUnderscoreMatcher.find() && italicUnderscoreMatcher.start() == 0) {
                return new FragmentResult(italicUnderscoreMatcher.group(1), FragmentType.ITALIC, null, italicUnderscoreMatcher.group().length());
            }

            Matcher codeMatcher = CODE_PATTERN.matcher(fromPosition);
            if (codeMatcher.find() && codeMatcher.start() == 0) {
                return new FragmentResult(codeMatcher.group(1), FragmentType.CODE, null, codeMatcher.group().length());
            }

            Matcher refUseMatcher = REFERENCE_USE_PATTERN.matcher(fromPosition);
            if (refUseMatcher.find() && refUseMatcher.start() == 0) {
                String refName = refUseMatcher.group(1).toLowerCase();
                String url = linkReferences.get(refName);
                if (url != null) {
                    return new FragmentResult(refUseMatcher.group(1), FragmentType.LINK, url, refUseMatcher.group().length());
                }
            }

            return null;
        }

        private void calculateLayout() {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int currentX = 0;
            int currentY = 0;
            int lineHeight = textRenderer.fontHeight + 2;

            for (TextFragment fragment : fragments) {
                String[] words = fragment.text.split("(?<=\\s)|(?=\\s)");

                for (String word : words) {
                    if (word.isEmpty()) continue;

                    int wordWidth = getWordWidth(word, fragment.type, textRenderer);

                    if (currentX + wordWidth > width && currentX > 0 && !word.trim().isEmpty()) {
                        currentY += lineHeight;
                        currentX = 0;
                    }

                    fragment.addWord(word, currentX, currentY, wordWidth);
                    currentX += wordWidth;
                }
            }

            this.height = currentY + lineHeight;
            this.bounds.height = height;
        }

        private int getWordWidth(String word, FragmentType type, TextRenderer textRenderer) {
            return switch (type) {
                case BOLD -> textRenderer.getWidth(Text.literal(word).formatted(Formatting.BOLD));
                default -> textRenderer.getWidth(word);
            };
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            for (TextFragment fragment : fragments) {
                fragment.render(baseX, baseY + y, context, textRenderer);
            }
        }

        @Override
        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            for (TextFragment fragment : fragments) {
                if (fragment.handleClick(baseX, baseY + y, mouseX, mouseY)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getHeight() {
            return height;
        }

        private enum FragmentType {
            TEXT, LINK, BOLD, ITALIC, CODE, IMAGE
        }

        private static class TextFragment {
            final String text;
            final int offset;
            final FragmentType type;
            final String url;
            final List<WordPosition> words = new ArrayList<>();

            public TextFragment(String text, int offset, FragmentType type, String url) {
                this.text = text;
                this.offset = offset;
                this.type = type;
                this.url = url;
            }

            public void addWord(String word, int x, int y, int width) {
                words.add(new WordPosition(word, x, y, width));
            }

            public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
                for (WordPosition word : words) {
                    int wordX = baseX + word.x;
                    int wordY = baseY + word.y;

                    switch (type) {
                        case LINK:
                            context.drawText(textRenderer, Text.literal(word.text).formatted(Formatting.UNDERLINE), wordX, wordY, LINK_COLOR, Config.shadow);
                            break;
                        case BOLD:
                            context.drawText(textRenderer, Text.literal(word.text).formatted(Formatting.BOLD), wordX, wordY, TEXT_COLOR, Config.shadow);
                            break;
                        case ITALIC:
                            context.drawText(textRenderer, Text.literal(word.text).formatted(Formatting.ITALIC), wordX, wordY, TEXT_COLOR, Config.shadow);
                            break;
                        case CODE:
                            context.fill(wordX - 2, wordY - 1, wordX + word.width + 2, wordY + textRenderer.fontHeight + 1, accentDarkColor);
                            context.drawText(textRenderer, Text.literal(word.text), wordX, wordY, globalDarkTextColor, Config.shadow);
                            break;
                        case IMAGE:
                            context.drawText(textRenderer, Text.literal(word.text).formatted(Formatting.ITALIC), wordX, wordY, 0xAAAAAA, Config.shadow);
                            break;
                        default:
                            context.drawText(textRenderer, Text.literal(word.text), wordX, wordY, TEXT_COLOR, Config.shadow);
                    }
                }
            }

            public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
                if (type == FragmentType.LINK && url != null) {
                    for (WordPosition word : words) {
                        int wordX = baseX + word.x;
                        int wordY = baseY + word.y;

                        if (mouseX >= wordX && mouseX <= wordX + word.width &&
                                mouseY >= wordY && mouseY <= wordY + MinecraftClient.getInstance().textRenderer.fontHeight) {

                            try {
                                String processedUrl = url.trim();
                                if (!processedUrl.startsWith("http://") && !processedUrl.startsWith("https://")) {
                                    processedUrl = "https://" + processedUrl;
                                }

                                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", processedUrl);
                                pb.start();
                                return true;
                            } catch (Exception e) {
                                return false;
                            }
                        }
                    }
                }
                return false;
            }
        }

        private static class WordPosition {
            final String text;
            final int x, y, width;

            public WordPosition(String text, int x, int y, int width) {
                this.text = text;
                this.x = x;
                this.y = y;
                this.width = width;
            }
        }

        private static class FragmentResult {
            final String text;
            final FragmentType type;
            final String url;
            final int consumed;

            public FragmentResult(String text, FragmentType type, String url, int consumed) {
                this.text = text;
                this.type = type;
                this.url = url;
                this.consumed = consumed;
            }
        }
    }

    private static class QuoteElement extends RenderElement {
        private final TextElement textElement;

        public QuoteElement(String text, int y, int width) {
            super(y, width);
            this.textElement = new TextElement(text, 0, width - 30);
            this.height = textElement.getHeight() + 16;
            this.bounds.height = height;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            int renderY = baseY + y;

            context.fill(baseX, renderY, baseX + 4, renderY + height, accentColor);
            context.fill(baseX + 4, renderY, baseX + width, renderY + height, accentDarkColor);

            textElement.render(baseX + 12, renderY + 10, context, textRenderer);
        }

        @Override
        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            return textElement.handleClick(baseX + 12, baseY + y + 10, mouseX, mouseY);
        }

        @Override
        public int getHeight() {
            return height;
        }
    }

    private static class CodeBlockElement extends RenderElement {
        private final String language;
        private final List<String> lines;

        public CodeBlockElement(String code, String language, int y, int width) {
            super(y, width);
            this.language = language;
            this.lines = Arrays.asList(code.split("\n"));
            this.height = calculateHeight();
            this.bounds.height = height;
        }

        private int calculateHeight() {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int headerHeight = (language != null && !language.isEmpty()) ? textRenderer.fontHeight : 0;
            return headerHeight + (lines.size() * (textRenderer.fontHeight + 2)) + 16;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            int renderY = baseY + y;

            context.fill(baseX, renderY, baseX + width, renderY + height, accentDarkColor);
            drawInnerBorder(context, baseX, renderY, width, height, accentColor);
            drawOuterBorder(context, baseX, renderY, width, height, accentDarkColor);


            int contentY = renderY + 2;

            if (language != null && !language.isEmpty()) {
                String lang = language.toUpperCase();
                int langWidth = textRenderer.getWidth(lang);
                context.fill(baseX + 2, contentY, baseX + langWidth + 16, contentY + textRenderer.fontHeight + 4, 0x44444444);
                context.drawText(textRenderer, Text.literal(lang), baseX + 12, contentY + 2, 0xCCCCCC, Config.shadow);
                contentY += textRenderer.fontHeight + 2;
            }

            for (String line : lines) {
                context.drawText(textRenderer, Text.literal(line), baseX + 2, contentY, 0xFFFFFF, Config.shadow);
                contentY += textRenderer.fontHeight + 2;
            }
        }

        @Override
        public int getHeight() {
            return height;
        }
    }

    private static class ImageElement extends RenderElement {
        private static final Pattern SVG_PATTERN = Pattern.compile("\\.(svg)($|\\?)|/svg/|/badge/|badges\\.|shields\\.io");
        private static final Pattern CHART_PATTERN = Pattern.compile("chart\\.cc|starchart|bstats\\.org/signatures");
        private static final int CONNECTION_TIMEOUT = 3000;
        private static final int READ_TIMEOUT = 3000;
        private static final long MAX_LOAD_TIME = 5000;
        private final String url;
        private final String alt;
        private BufferedImage image;
        private boolean loading = false;
        private boolean loaded = false;
        private boolean failed = false;
        private boolean isSvg = false;
        private boolean isShield = false;
        private boolean timeout = false;
        private long loadStartTime = 0;
        private int imageWidth = 200;
        private int imageHeight = 120;

        public ImageElement(String url, String alt, int y, int width) {
            super(y, width);
            this.url = url;
            this.alt = alt != null ? alt : "";
            this.height = calculateHeight();
            this.bounds.height = height;
            startLoadImage();
        }

        public int getImageWidth() {
            return imageWidth;
        }

        public int getImageHeight() {
            return imageHeight;
        }

        private void startLoadImage() {
            if (loading || loaded || url == null || url.trim().isEmpty()) return;

            loading = true;
            loadStartTime = System.currentTimeMillis();

            if (SVG_PATTERN.matcher(url.toLowerCase()).find()) {
                isSvg = true;
                failed = true;
                loaded = true;
                loading = false;
                return;
            }

            if (CHART_PATTERN.matcher(url.toLowerCase()).find()) {
                isShield = true;
                failed = true;
                loaded = true;
                loading = false;
                return;
            }

            if (imageCache.containsKey(url)) {
                image = imageCache.get(url);
                updateDimensions();
                loaded = true;
                loading = false;
                return;
            }

            imageLoader.submit(new ImageLoadTask());
        }

        private synchronized void finishLoading() {
            loaded = true;
            loading = false;
        }

        private void updateDimensions() {
            if (image != null) {
                int maxWidth = Math.min(400, width - 40);
                int maxHeight = 300;

                int originalWidth = image.getWidth();
                int originalHeight = image.getHeight();

                if (originalWidth > maxWidth || originalHeight > maxHeight) {
                    float widthRatio = (float) maxWidth / originalWidth;
                    float heightRatio = (float) maxHeight / originalHeight;
                    float ratio = Math.min(widthRatio, heightRatio);

                    imageWidth = (int) (originalWidth * ratio);
                    imageHeight = (int) (originalHeight * ratio);
                } else {
                    imageWidth = originalWidth;
                    imageHeight = originalHeight;
                }

                this.height = calculateHeight();
                this.bounds.height = height;
            }
        }

        private int calculateHeight() {
            return imageHeight + 30;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            if (loading && System.currentTimeMillis() - loadStartTime > MAX_LOAD_TIME) {
                loading = false;
                loaded = true;
                timeout = true;
                failed = true;
            }

            int renderY = baseY + y;

            if (loaded && image != null && !failed) {
                try {
                    ImageUtil.drawBufferedImage(context, image, baseX, renderY, imageWidth, imageHeight);
                } catch (Exception e) {
                    context.drawText(textRenderer, Text.literal("Error displaying image"), baseX, renderY, 0xFF5555, Config.shadow);
                }
            } else {
                String status;
                if (isSvg) {
                    status = "SVG format not supported";
                } else if (isShield) {
                    status = "Badge/Chart not supported";
                } else if (timeout) {
                    status = "Image load timed out";
                } else {
                    status = loading ? "Loading..." : failed ? "Failed to load" : "Image";
                }

                if (!alt.isEmpty()) {
                    status += ": " + alt;
                }
                context.drawText(textRenderer, Text.literal(status), baseX, renderY, 0xAAAAAA, Config.shadow);
            }
        }

        @Override
        public int getHeight() {
            return height;
        }

        private class ImageLoadTask implements Runnable {
            @Override
            public void run() {
                try {
                    URL imageUrl = new URL(url);

                    java.net.URLConnection connection = imageUrl.openConnection();
                    connection.setConnectTimeout(CONNECTION_TIMEOUT);
                    connection.setReadTimeout(READ_TIMEOUT);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 Remotely/1.0");

                    String contentType = connection.getContentType();
                    if (contentType != null && (contentType.toLowerCase().contains("svg") ||
                            contentType.toLowerCase().contains("xml"))) {
                        isSvg = true;
                        failed = true;
                        finishLoading();
                        return;
                    }

                    try (InputStream in = connection.getInputStream()) {
                        BufferedImage loadedImage = ImageIO.read(in);
                        if (loadedImage != null) {
                            image = loadedImage;
                            imageCache.put(url, image);
                            updateDimensions();
                        } else {
                            failed = true;
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    timeout = true;
                    failed = true;
                } catch (Exception e) {
                    failed = true;
                } finally {
                    finishLoading();
                }
            }
        }
    }

    private static class TableElement extends RenderElement {
        private final List<List<String>> rows = new ArrayList<>();
        private final List<List<CellElement>> cellElements = new ArrayList<>();
        private final List<Integer> columnWidths = new ArrayList<>();
        private final List<Integer> rowHeights = new ArrayList<>();
        private boolean hasHeader = false;
        private int columnCount = 0;

        public TableElement(List<String> tableLines, int y, int width) {
            super(y, width);
            parseTable(tableLines);
            calculateDimensions();
            this.height = calculateTotalHeight();
            this.bounds.height = height;
        }

        private void parseTable(List<String> lines) {
            int separatorIndex = -1;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (TABLE_SEPARATOR.matcher(line).matches()) {
                    hasHeader = i > 0;
                    separatorIndex = i;
                    break;
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();

                if (i == separatorIndex) {
                    continue;
                }

                if (TABLE_ROW_PATTERN.matcher(line).matches()) {
                    if (line.startsWith("|")) line = line.substring(1);
                    if (line.endsWith("|")) line = line.substring(0, line.length() - 1);

                    String[] cells = line.split("\\|", -1);
                    List<String> row = new ArrayList<>();
                    List<CellElement> cellElementRow = new ArrayList<>();

                    for (String cell : cells) {
                        String trimmedCell = cell.trim();
                        row.add(trimmedCell);

                        CellElement cellElement = createCellElement(trimmedCell, 100);
                        cellElementRow.add(cellElement);
                    }

                    rows.add(row);
                    cellElements.add(cellElementRow);
                    columnCount = Math.max(columnCount, row.size());
                }
            }

            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                List<CellElement> cellRow = cellElements.get(i);

                while (row.size() < columnCount) {
                    row.add("");
                    cellRow.add(createCellElement("", 100));
                }
            }
        }

        private CellElement createCellElement(String content, int width) {
            Matcher imageMatcher = IMAGE_PATTERN.matcher(content.trim());
            if (imageMatcher.find() && imageMatcher.start() == 0) {
                String url = imageMatcher.group(2);
                String alt = imageMatcher.group(1);
                return new CellElement(new ImageElement(url, alt, 0, width), CellType.IMAGE);
            } else {
                return new CellElement(new TextElement(content, 0, width), CellType.TEXT);
            }
        }

        private void calculateDimensions() {
            if (rows.isEmpty() || columnCount == 0) return;

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            columnWidths.clear();

            for (int col = 0; col < columnCount; col++) {
                int maxWidth = 60;

                for (int row = 0; row < cellElements.size(); row++) {
                    List<CellElement> cellRow = cellElements.get(row);
                    if (col < cellRow.size()) {
                        CellElement cellElement = cellRow.get(col);

                        if (cellElement.type == CellType.IMAGE && cellElement.element instanceof ImageElement) {
                            ImageElement imageElement = (ImageElement) cellElement.element;
                            int imageWidth = imageElement.getImageWidth() + 4;
                            maxWidth = Math.max(maxWidth, imageWidth);
                        } else if (cellElement.type == CellType.TEXT) {
                            String cellContent = rows.get(row).get(col);
                            int contentWidth = textRenderer.getWidth(cellContent) + 10;
                            maxWidth = Math.max(maxWidth, contentWidth);
                        }
                    }
                }

                columnWidths.add(maxWidth);
            }

            int totalWidth = columnWidths.stream().mapToInt(Integer::intValue).sum();
            int availableWidth = width - (columnCount + 1) - 10;

            if (totalWidth > availableWidth) {
                double scale = (double) availableWidth / totalWidth;
                columnWidths.replaceAll(integer -> Math.max(40, (int) (integer * scale)));
            } else if (totalWidth < availableWidth) {
                int extraWidth = availableWidth - totalWidth;
                int extraPerColumn = extraWidth / columnCount;
                int remainder = extraWidth % columnCount;

                for (int i = 0; i < columnWidths.size(); i++) {
                    columnWidths.set(i, columnWidths.get(i) + extraPerColumn + (i < remainder ? 1 : 0));
                }
            }

            rowHeights.clear();
            for (int row = 0; row < cellElements.size(); row++) {
                int maxRowHeight = textRenderer.fontHeight + 8;
                List<CellElement> rowElements = cellElements.get(row);

                for (int col = 0; col < rowElements.size() && col < columnWidths.size(); col++) {
                    CellElement cellElement = rowElements.get(col);

                    if (cellElement.type == CellType.IMAGE && cellElement.element instanceof ImageElement) {
                        ImageElement imageElement = (ImageElement) cellElement.element;
                        int imageHeight = imageElement.getImageHeight() + 4;
                        maxRowHeight = Math.max(maxRowHeight, imageHeight);
                    } else if (cellElement.type == CellType.TEXT) {
                        String cellContent = rows.get(row).get(col);
                        TextElement textElement = new TextElement(cellContent, 0, columnWidths.get(col) - 10);
                        cellElement.element = textElement;

                        int cellHeight = textElement.getHeight() + 8;
                        maxRowHeight = Math.max(maxRowHeight, cellHeight);
                    }
                }

                rowHeights.add(maxRowHeight);
            }
        }

        private int calculateTotalHeight() {
            if (rowHeights.isEmpty()) return 20;
            return rowHeights.stream().mapToInt(Integer::intValue).sum() + rowHeights.size() + 1;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            if (rows.isEmpty() || columnCount == 0) return;
            int currentY = baseY + y;

            int tableWidth = columnWidths.stream().mapToInt(Integer::intValue).sum() + columnCount + 1;
            int height = getHeight() - 8;
            context.fill(baseX, currentY, baseX + tableWidth, currentY + height, backgroundColor);
            drawOuterBorder(context, baseX, currentY, tableWidth, height, elementBackgroundColor);

            for (int row = 0; row < rows.size(); row++) {
                int rowHeight = rowHeights.get(row);
                int currentX = baseX;

                if (hasHeader && row == 0) {
                    context.fill(currentX, currentY, currentX + tableWidth, currentY + rowHeight, elementBackgroundColor);
                }

                context.fill(currentX, currentY, currentX + 1, currentY + rowHeight, innerBorderColor);
                currentX++;

                List<CellElement> rowElements = cellElements.get(row);
                for (int col = 0; col < columnCount; col++) {
                    int colWidth = columnWidths.get(col);

                    if (col < rowElements.size()) {
                        CellElement cellElement = rowElements.get(col);
                        if (cellElement != null && cellElement.element != null) {
                            int cellContentHeight = cellElement.type == CellType.IMAGE ?
                                    ((ImageElement) cellElement.element).getImageHeight() :
                                    cellElement.element.getHeight();
                            int verticalPadding = Math.max(0, (rowHeight - cellContentHeight) / 2);

                            cellElement.element.render(currentX + 2, currentY + verticalPadding, context, textRenderer);
                        }
                    }

                    currentX += colWidth;

                    context.fill(currentX, currentY, currentX + 1, currentY + rowHeight, innerBorderColor);
                    currentX++;
                }

                currentY += rowHeight;

                context.fill(baseX, currentY, baseX + tableWidth, currentY + 1, innerBorderColor);
            }
        }

        @Override
        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            if (rows.isEmpty() || columnCount == 0) return false;

            int renderY = baseY + y;
            int currentY = renderY + 1;

            for (int row = 0; row < cellElements.size(); row++) {
                int rowHeight = rowHeights.get(row);

                if (mouseY >= currentY && mouseY <= currentY + rowHeight) {
                    int currentX = baseX + 1;
                    List<CellElement> rowElements = cellElements.get(row);

                    for (int col = 0; col < columnCount && col < rowElements.size(); col++) {
                        int colWidth = columnWidths.get(col);

                        if (mouseX >= currentX && mouseX <= currentX + colWidth) {
                            CellElement cellElement = rowElements.get(col);
                            if (cellElement != null && cellElement.element != null) {
                                int cellContentHeight = cellElement.type == CellType.IMAGE ?
                                        ((ImageElement) cellElement.element).getImageHeight() :
                                        cellElement.element.getHeight();
                                int verticalPadding = Math.max(0, (rowHeight - cellContentHeight) / 2);

                                return cellElement.element.handleClick(currentX + 2, currentY + verticalPadding, mouseX, mouseY);
                            }
                            return false;
                        }

                        currentX += colWidth + 1;
                    }
                    return false;
                }

                currentY += rowHeight + 1;
            }

            return false;
        }

        @Override
        public int getHeight() {
            return height;
        }

        private enum CellType {
            TEXT, IMAGE
        }

        private static class CellElement {
            RenderElement element;
            CellType type;

            public CellElement(RenderElement element, CellType type) {
                this.element = element;
                this.type = type;
            }
        }
    }

    private static class ListElement extends RenderElement {
        private final List<ListItem> items = new ArrayList<>();
        private final boolean ordered;

        public ListElement(List<String> lines, boolean ordered, int y, int width) {
            super(y, width);
            this.ordered = ordered;
            parseList(lines);
            this.height = calculateHeight();
            this.bounds.height = height;
        }

        private void parseList(List<String> lines) {
            Pattern pattern = ordered ? ORDERED_LIST_PATTERN : LIST_PATTERN;
            int index = 1;

            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String indent = matcher.group(1);
                    String content = matcher.group(2);
                    int depth = indent.length() / 2;

                    ListItem item = new ListItem(content, depth, ordered ? index++ : 0);
                    items.add(item);
                }
            }
        }

        private int calculateHeight() {
            int totalHeight = 0;

            for (ListItem item : items) {
                int itemWidth = width - (item.depth * 20) - 30;
                item.textElement = new TextElement(item.content, 0, Math.max(50, itemWidth));
                totalHeight += item.textElement.getHeight() + 4;
            }

            return totalHeight;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            int currentY = baseY + y;

            for (ListItem item : items) {
                int itemX = baseX + (item.depth * 20);
                String marker = ordered ? (item.index + ". ") : "• ";
                int markerWidth = textRenderer.getWidth(marker);

                context.drawText(textRenderer, Text.literal(marker), itemX, currentY, TEXT_COLOR, Config.shadow);

                if (item.textElement != null) {
                    item.textElement.render(itemX + markerWidth + 5, currentY, context, textRenderer);
                    currentY += item.textElement.getHeight() + 4;
                }
            }
        }

        @Override
        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            int currentY = baseY + y;

            for (ListItem item : items) {
                if (item.textElement != null) {
                    int itemX = baseX + (item.depth * 20);
                    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
                    String marker = ordered ? (item.index + ". ") : "• ";
                    int markerWidth = textRenderer.getWidth(marker);

                    if (item.textElement.handleClick(itemX + markerWidth + 5, currentY, mouseX, mouseY)) {
                        return true;
                    }

                    currentY += item.textElement.getHeight() + 4;
                }
            }

            return false;
        }

        @Override
        public int getHeight() {
            return height;
        }

        private static class ListItem {
            final String content;
            final int depth;
            final int index;
            TextElement textElement;

            public ListItem(String content, int depth, int index) {
                this.content = content;
                this.depth = depth;
                this.index = index;
            }
        }
    }

    private class HtmlElement extends RenderElement {
        private final String tag;
        private final String attributes;
        private final String content;
        private final Map<String, String> parsedAttributes = new HashMap<>();
        private RenderElement contentElement;

        public HtmlElement(String tag, String attributes, String content, int y, int width) {
            super(y, width);
            this.tag = tag.toLowerCase();
            this.attributes = attributes != null ? attributes : "";
            this.content = content != null ? content : "";
            parseAttributes();
            createContentElement();
            this.height = calculateHeight();
            this.bounds.height = height;
        }

        private void parseAttributes() {
            if (attributes.isEmpty()) return;

            Pattern attrPattern = Pattern.compile("(\\w+)\\s*=\\s*[\"']([^\"']*)[\"']");
            Matcher matcher = attrPattern.matcher(attributes);

            while (matcher.find()) {
                parsedAttributes.put(matcher.group(1).toLowerCase(), matcher.group(2));
            }
        }

        private void createContentElement() {
            switch (tag) {
                case "div":
                case "span":
                case "p":
                    if (!content.isEmpty()) {
                        String processedContent = BR_TAG_PATTERN.matcher(content).replaceAll("\n");
                        contentElement = new TextElement(processedContent, 0, width - 20);
                    }
                    break;
                case "img":
                    String src = parsedAttributes.get("src");
                    String alt = parsedAttributes.get("alt");
                    if (src != null) {
                        contentElement = new ImageElement(src, alt, 0, width - 20);
                    }
                    break;
                case "a":
                    String href = parsedAttributes.get("href");
                    if (!content.isEmpty() && href != null) {
                        contentElement = new ClickableLinkElement(content, href, 0, width - 20);
                    } else if (!content.isEmpty()) {
                        String processedContent = BR_TAG_PATTERN.matcher(content).replaceAll("\n");
                        contentElement = new TextElement(processedContent, 0, width - 20);
                    }
                    break;
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    int level = Character.getNumericValue(tag.charAt(1));
                    contentElement = new HeaderElement(content, level, 0, width - 20);
                    break;
                case "code":
                    contentElement = new TextElement("`" + content + "`", 0, width - 20);
                    break;
                case "pre":
                    contentElement = new CodeBlockElement(content, "", 0, width - 20);
                    break;
                case "br":
                    contentElement = new LineBreakElement(0, width - 20);
                    break;
                default:
                    if (!content.isEmpty()) {
                        String processedContent = BR_TAG_PATTERN.matcher(content).replaceAll("\n");
                        contentElement = new TextElement(processedContent, 0, width - 20);
                    }
            }
        }

        private int calculateHeight() {
            int contentHeight = contentElement != null ? contentElement.getHeight() : 0;
            return contentHeight + getPadding() * 2;
        }

        private int getPadding() {
            return tag.equals("div") || tag.equals("blockquote") ? 10 : 5;
        }

        @Override
        public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            int renderY = baseY + y;
            int padding = getPadding();

            if (tag.equals("div")) {
                context.fill(baseX, renderY, baseX + width, renderY + height, 0x22222233);
                context.fill(baseX, renderY, baseX + width, renderY + 1, 0x44444444);
                context.fill(baseX, renderY, baseX + 1, renderY + height, 0x44444444);
                context.fill(baseX, renderY + height - 1, baseX + width, renderY + height, 0x44444444);
                context.fill(baseX + width - 1, renderY, baseX + width, renderY + height, 0x44444444);
            } else if (tag.equals("blockquote")) {
                context.fill(baseX, renderY, baseX + 4, renderY + height, 0x666699FF);
                context.fill(baseX + 4, renderY, baseX + width, renderY + height, 0x33336622);
            }

            if (contentElement != null) {
                contentElement.render(baseX + padding, renderY + padding, context, textRenderer);
            }
        }

        @Override
        public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
            if (contentElement != null) {
                int padding = getPadding();
                return contentElement.handleClick(baseX + padding, baseY + y + padding, mouseX, mouseY);
            }
            return false;
        }

        @Override
        public int getHeight() {
            return height;
        }

        private static class ClickableLinkElement extends RenderElement {
            private final String text;
            private final String url;

            public ClickableLinkElement(String text, String url, int y, int width) {
                super(y, width);
                this.text = text;
                this.url = url;
                this.height = MinecraftClient.getInstance().textRenderer.fontHeight;
                this.bounds.height = height;
                this.bounds.width = MinecraftClient.getInstance().textRenderer.getWidth(text);
            }

            @Override
            public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
                context.drawText(textRenderer, Text.literal(text).formatted(Formatting.UNDERLINE), baseX, baseY + y, LINK_COLOR, Config.shadow);
            }

            @Override
            public boolean handleClick(int baseX, int baseY, float mouseX, float mouseY) {
                if (isMouseOver(baseX, baseY, mouseX, mouseY)) {
                    try {
                        String processedUrl = url.trim();
                        if (!processedUrl.startsWith("http://") && !processedUrl.startsWith("https://")) {
                            processedUrl = "https://" + processedUrl;
                        }
                        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", processedUrl);
                        pb.start();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }

            @Override
            public int getHeight() {
                return height;
            }
        }

        private static class LineBreakElement extends RenderElement {
            public LineBreakElement(int y, int width) {
                super(y, width);
                this.height = 10;
                this.bounds.height = height;
            }

            @Override
            public void render(int baseX, int baseY, DrawContext context, TextRenderer textRenderer) {
            }

            @Override
            public int getHeight() {
                return height;
            }
        }
    }
}