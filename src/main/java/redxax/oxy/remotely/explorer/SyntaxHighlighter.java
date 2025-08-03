package redxax.oxy.remotely.explorer;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import redxax.oxy.remotely.config.Config;

public class SyntaxHighlighter {
    private static final Pattern CODE_COMMENT_PATTERN = Pattern.compile("//.*|/\\*.*\\*/|#.*");
    private static final Pattern GLOBAL_VAR_PATTERN = Pattern.compile("^\\s*(?:public|private|protected|static|final)\\s+\\S+\\s+([a-zA-Z_]\\w*)(?=\\s|=|;|\\()?");
    private static final Pattern LOCAL_VAR_PATTERN = Pattern.compile("\\b(?:int|String|boolean|var|let|const)\\s+([a-zA-Z_]\\w*)(?=\\s|=|;|\\()?");
    private static final Pattern CODE_KEYWORD_PATTERN = Pattern.compile("\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern BOOL_PATTERN = Pattern.compile("\\b(true|false)\\b");
    private static final Pattern HEX_COLOR_DYNAMIC_PATTERN = Pattern.compile("(?<!\\w)#[0-9A-Fa-f]{3,8}(?!\\w)");

    private static final Pattern YAML_COMMENT = Pattern.compile("^(\\s)*#.*");
    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"]+)\":");
    private static final Pattern TOML_KEY = Pattern.compile("^[^=]+=");
    private static final Pattern PROPERTIES_KEY = Pattern.compile("^[^=]+=");

    private static final Map<String, List<PatternHighlighter>> extensionPatterns = new HashMap<>();
    private static final List<Integer> BRACKET_COLORS = Arrays.asList(0xDCDCAA, 0xC586C0, 0xD7BA7D, 0x4EC9B0, 0x569CD6, 0x9CDCFE);

    private static final Map<Character, Character> BRACKET_PAIRS = new HashMap<>();
    static {
        BRACKET_PAIRS.put('(', ')');
        BRACKET_PAIRS.put('{', '}');
        BRACKET_PAIRS.put('[', ']');
        BRACKET_PAIRS.put('<', '>');
    }

    static {
        List<PatternHighlighter> codePatterns = new ArrayList<>();
        codePatterns.add(new SimplePatternHighlighter(CODE_COMMENT_PATTERN, Config.syntaxCommentColor, 10));
        codePatterns.add(new SubgroupPatternHighlighter(GLOBAL_VAR_PATTERN, 8, Config.syntaxGlobalVarColor));
        codePatterns.add(new SubgroupPatternHighlighter(LOCAL_VAR_PATTERN, 7, Config.syntaxLocalVarColor));
        codePatterns.add(new SimplePatternHighlighter(CODE_KEYWORD_PATTERN, Config.syntaxKeywordColor, 6));
        codePatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 5));
        codePatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 4));
        codePatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 3));
        codePatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> yamlPatterns = new ArrayList<>();
        yamlPatterns.add(new SimplePatternHighlighter(YAML_COMMENT, Config.syntaxCommentColor, 10));
        yamlPatterns.add(new SimplePatternHighlighter(TOML_KEY, Config.syntaxKeyColor, 5));
        yamlPatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 4));
        yamlPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 3));
        yamlPatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 2));
        yamlPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> jsonPatterns = new ArrayList<>();
        jsonPatterns.add(new SubgroupPatternHighlighter(JSON_KEY, 1, Config.syntaxKeyColor));
        jsonPatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 4));
        jsonPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 3));
        jsonPatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 2));
        jsonPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> tomlPatterns = new ArrayList<>();
        tomlPatterns.add(new SimplePatternHighlighter(TOML_KEY, Config.syntaxKeyColor, 5));
        tomlPatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 4));
        tomlPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 3));
        tomlPatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 2));
        tomlPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> propertiesPatterns = new ArrayList<>();
        propertiesPatterns.add(new SimplePatternHighlighter(PROPERTIES_KEY, Config.syntaxKeyColor, 5));
        propertiesPatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 4));
        propertiesPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 3));
        propertiesPatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 2));
        propertiesPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> textPatterns = new ArrayList<>();
        textPatterns.add(new SimplePatternHighlighter(STRING_PATTERN, Config.syntaxStringColor, 3));
        textPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 2));
        textPatterns.add(new SimplePatternHighlighter(BOOL_PATTERN, Config.syntaxBooleanColor, 2));
        textPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        List<PatternHighlighter> pyPatterns = new ArrayList<>();
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("#.*"), Config.syntaxCommentColor, 10));
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("f?\"([^\"\\\\]|\\\\.)*\"|f?'([^'\\\\]|\\\\.)*'"), Config.syntaxStringColor, 5));
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("\\b(def|class|from|import|return|if|elif|else|for|while|try|except|finally|with|as|pass|break|continue|lambda|yield|global|nonlocal|del|async|await|in|is|not|and|or)\\b"), Config.syntaxKeywordColor, 6));
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("\\b(self|cls)\\b"), Config.syntaxKeywordColor, 8));
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("@[a-zA-Z_]\\w*"), Config.syntaxLocalVarColor, 9));
        pyPatterns.add(new SimplePatternHighlighter(Pattern.compile("\\b(True|False|None)\\b"), Config.syntaxBooleanColor, 3));
        pyPatterns.add(new SimplePatternHighlighter(NUMBER_PATTERN, Config.syntaxNumberColor, 4));
        pyPatterns.add(new DynamicColorPatternHighlighter(HEX_COLOR_DYNAMIC_PATTERN, 11, HexColorResolver.INSTANCE));

        extensionPatterns.put("java", codePatterns);
        extensionPatterns.put("js", codePatterns);
        extensionPatterns.put("ts", codePatterns);
        extensionPatterns.put("cpp", codePatterns);
        extensionPatterns.put("c", codePatterns);
        extensionPatterns.put("cs", codePatterns);

        extensionPatterns.put("py", pyPatterns);

        extensionPatterns.put("yaml", yamlPatterns);
        extensionPatterns.put("yml", yamlPatterns);
        extensionPatterns.put("json", jsonPatterns);
        extensionPatterns.put("toml", tomlPatterns);
        extensionPatterns.put("properties", propertiesPatterns);
        extensionPatterns.put("txt", textPatterns);
    }

    public static Text highlight(String line, String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        List<PatternHighlighter> highlighters = extensionPatterns.getOrDefault(ext, new ArrayList<>());

        List<MatchResult> matchResults = new ArrayList<>(getBracketMatches(line));

        for (PatternHighlighter ph : highlighters) {
            matchResults.addAll(ph.findMatches(line));
        }

        matchResults.sort(Comparator
                .comparingInt(MatchResult::start)
                .thenComparingInt(MatchResult::priority)
                .reversed());

        List<MatchResult> filtered = new ArrayList<>();
        boolean[] occupied = new boolean[line.length()];
        for (MatchResult mr : matchResults) {
            boolean overlap = false;
            for (int i = mr.start; i < mr.end; i++) {
                if (i >= 0 && i < occupied.length && occupied[i]) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                filtered.add(mr);
                for (int i = mr.start; i < mr.end && i < occupied.length; i++) {
                    occupied[i] = true;
                }
            }
        }

        filtered.sort(Comparator.comparingInt(MatchResult::start));
        MutableText mutableText = Text.literal("");
        int lastIndex = 0;
        for (MatchResult m : filtered) {
            if (m.start > lastIndex) {
                mutableText.append(Text.literal(line.substring(lastIndex, m.start)));
            }
            mutableText.append(Text.literal(line.substring(m.start, m.end)).styled(s -> s.withColor(m.color)));
            lastIndex = m.end;
        }
        if (lastIndex < line.length()) {
            mutableText.append(Text.literal(line.substring(lastIndex)));
        }
        return mutableText;
    }

    private static List<MatchResult> getBracketMatches(String line) {
        List<MatchResult> results = new ArrayList<>();
        Deque<BracketStackEntry> stack = new ArrayDeque<>();
        int colorIndex = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (BRACKET_PAIRS.containsKey(c)) {
                int color = BRACKET_COLORS.get(colorIndex % BRACKET_COLORS.size());
                stack.push(new BracketStackEntry(c, i, color));
                results.add(new MatchResult(i, i+1, color, 9));
                colorIndex++;
            } else if (BRACKET_PAIRS.containsValue(c)) {
                Optional<Character> matchingOpen = BRACKET_PAIRS.entrySet().stream()
                        .filter(e -> e.getValue() == c)
                        .map(Map.Entry::getKey)
                        .findFirst();
                if (matchingOpen.isPresent() && !stack.isEmpty() && stack.peek().ch == matchingOpen.get()) {
                    BracketStackEntry open = stack.pop();
                    results.add(new MatchResult(i, i + 1, open.color, 9));
                }
            }
        }
        return results;
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }

    private interface PatternHighlighter {
        List<MatchResult> findMatches(String line);
    }

    private record SimplePatternHighlighter(Pattern pattern, int color, int priority) implements PatternHighlighter {

        @Override
            public List<MatchResult> findMatches(String line) {
                List<MatchResult> list = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    list.add(new MatchResult(matcher.start(), matcher.end(), color, priority));
                }
                return list;
            }
        }

    private static class SubgroupPatternHighlighter implements PatternHighlighter {
        private final Pattern pattern;
        private final int priority;
        private final int subgroupColor;
        private final int group;

        SubgroupPatternHighlighter(int group, Pattern pattern, int priority, int subgroupColor) {
            this.pattern = pattern;
            this.priority = priority;
            this.subgroupColor = subgroupColor;
            this.group = group;
        }

        SubgroupPatternHighlighter(Pattern pattern, int priority, int subgroupColor) {
            this(1, pattern, priority, subgroupColor);
        }

        @Override
        public List<MatchResult> findMatches(String line) {
            List<MatchResult> list = new ArrayList<>();
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                if (matcher.groupCount() >= group && matcher.group(group) != null) {
                    int start = matcher.start(group);
                    int end = matcher.end(group);
                    list.add(new MatchResult(start, end, subgroupColor, priority));
                }
            }
            return list;
        }
    }

    private record DynamicColorPatternHighlighter(Pattern pattern, int priority, Function<String, Integer> colorResolver) implements PatternHighlighter {

        @Override
            public List<MatchResult> findMatches(String line) {
                List<MatchResult> list = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    String match = line.substring(matcher.start(), matcher.end());
                    if (match.startsWith("#")) {
                        int color = colorResolver.apply(match);
                        list.add(new MatchResult(matcher.start(), matcher.end(), color, priority));
                    }
                }
                return list;
            }
        }
    private static class HexColorResolver implements Function<String, Integer> {
        static final HexColorResolver INSTANCE = new HexColorResolver();

        @Override
        public Integer apply(String match) {
            String hex = match.replace("#", "").trim();
            int a = 255;
            int r, g, b;
            try {
                int r1 = Integer.parseInt(String.valueOf(hex.charAt(0)) + hex.charAt(0), 16);
                int g1 = Integer.parseInt(String.valueOf(hex.charAt(1)) + hex.charAt(1), 16);
                int b1 = Integer.parseInt(String.valueOf(hex.charAt(2)) + hex.charAt(2), 16);
                switch (hex.length()) {
                    case 3:
                        r = r1;
                        g = g1;
                        b = b1;
                        break;
                    case 4:
                        a = r1;
                        r = g1;
                        g = b1;
                        b = Integer.parseInt(String.valueOf(hex.charAt(3)) + hex.charAt(3), 16);
                        break;
                    case 6:
                        r = Integer.parseInt(hex.substring(0, 2), 16);
                        g = Integer.parseInt(hex.substring(2, 4), 16);
                        b = Integer.parseInt(hex.substring(4, 6), 16);
                        break;
                    case 8:
                        a = Integer.parseInt(hex.substring(0, 2), 16);
                        r = Integer.parseInt(hex.substring(2, 4), 16);
                        g = Integer.parseInt(hex.substring(4, 6), 16);
                        b = Integer.parseInt(hex.substring(6, 8), 16);
                        break;
                    default: return 0xFFFFFF;
                }
            } catch (NumberFormatException e) {return 0xFFFFFF;}

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private record MatchResult(int start, int end, int color, int priority) {}

    private static class BracketStackEntry {
        char ch;
        int index;
        int color;

        BracketStackEntry(char ch, int index, int color) {
            this.ch = ch;
            this.index = index;
            this.color = color;
        }
    }
}