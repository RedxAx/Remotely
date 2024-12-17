package redxax.oxy.fileeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {
    private static final Pattern YAML_COMMENT = Pattern.compile("^\\s*#.*");
    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"]+)\":");
    private static final Pattern TOML_KEY = Pattern.compile("^\\s*[^#]+=");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern BOOL_PATTERN = Pattern.compile("\\b(true|false)\\b");

    public static String highlight(String line, String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        line = colorPatterns(line, NUMBER_PATTERN, 0xB5CEA8); // Green for numbers
        line = colorPatterns(line, BOOL_PATTERN, 0x569CD6);   // Blue for booleans
        line = colorPatterns(line, STRING_PATTERN, 0xCE9178); // Light Orange for strings

        if (ext.equals("yaml") || ext.equals("yml")) {
            if (YAML_COMMENT.matcher(line).find()) {
                line = colorAll(line, 0x6A9955); // Green for comments
            } else {
                line = colorPatterns(line, Pattern.compile("^[^:]+:"), 0x9CDCFE); // Light Blue for keys
            }
        } else if (ext.equals("json")) {
            line = colorPatterns(line, JSON_KEY, 0x9CDCFE); // Light Blue for JSON keys
        } else if (ext.equals("toml")) {
            line = colorPatterns(line, TOML_KEY, 0x9CDCFE); // Light Blue for TOML keys
        }
        return line;
    }

    private static String colorPatterns(String text, Pattern pattern, int rgb) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String colored = colorAll(match, rgb);
            matcher.appendReplacement(sb, colored);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String colorAll(String text, int rgb) {
        return "§x" + hex(rgb) + text + "§r";
    }

    private static String hex(int rgb) {
        String hex = String.format("%06X", (rgb & 0xFFFFFF));
        StringBuilder sb = new StringBuilder();
        for (char c : hex.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
