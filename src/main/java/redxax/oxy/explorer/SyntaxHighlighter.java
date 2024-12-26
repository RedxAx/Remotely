package redxax.oxy.explorer;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class SyntaxHighlighter {
    private static final Pattern YAML_COMMENT = Pattern.compile("^\\s*#.*");
    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"]+)\":");
    private static final Pattern TOML_KEY = Pattern.compile("^\\s*[^#]+=");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern BOOL_PATTERN = Pattern.compile("\\b(true|false)\\b");

    public static Text highlight(String line, String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        MutableText mutableText = Text.literal("");
        int lastIndex = 0;

        ArrayList<PatternColorPair> patterns = new ArrayList<>();
        patterns.add(new PatternColorPair(NUMBER_PATTERN, 0xB5CEA8));
        patterns.add(new PatternColorPair(BOOL_PATTERN, 0x569CD6));
        patterns.add(new PatternColorPair(STRING_PATTERN, 0xCE9178));

        for (PatternColorPair pair : patterns) {
            Matcher matcher = pair.pattern.matcher(line);
            while (matcher.find()) {
                if (matcher.start() > lastIndex) {
                    mutableText.append(Text.literal(line.substring(lastIndex, matcher.start())));
                }
                mutableText.append(Text.literal(matcher.group()).styled(style -> style.withColor(pair.color)));
                lastIndex = matcher.end();
            }
        }

        if (lastIndex < line.length()) {
            mutableText.append(Text.literal(line.substring(lastIndex)));
        }

        if (ext.equals("yaml") || ext.equals("yml")) {
            Matcher commentMatcher = YAML_COMMENT.matcher(line);
            if (commentMatcher.find()) {
                return Text.literal(line).styled(style -> style.withColor(0x6A9955));
            } else {
                Matcher keyMatcher = Pattern.compile("^[^:]+:").matcher(line);
                if (keyMatcher.find()) {
                    MutableText keyText = Text.literal(keyMatcher.group()).styled(style -> style.withColor(0x9CDCFE));
                    MutableText restText = Text.literal(line.substring(keyMatcher.end()));
                    return Text.empty().append(keyText).append(restText);
                }
            }
        } else if (ext.equals("json")) {
            Matcher keyMatcher = JSON_KEY.matcher(line);
            if (keyMatcher.find()) {
                MutableText keyText = Text.literal(keyMatcher.group()).styled(style -> style.withColor(0x9CDCFE));
                MutableText restText = Text.literal(line.substring(keyMatcher.end()));
                return Text.empty().append(keyText).append(restText);
            }
        } else if (ext.equals("toml")) {
            Matcher keyMatcher = TOML_KEY.matcher(line);
            if (keyMatcher.find()) {
                MutableText keyText = Text.literal(keyMatcher.group()).styled(style -> style.withColor(0x9CDCFE));
                MutableText restText = Text.literal(line.substring(keyMatcher.end()));
                return Text.empty().append(keyText).append(restText);
            }
        }

        return mutableText;
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }

    private static class PatternColorPair {
        Pattern pattern;
        int color;

        PatternColorPair(Pattern pattern, int color) {
            this.pattern = pattern;
            this.color = color;
        }
    }
}
