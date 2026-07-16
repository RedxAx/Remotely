package redxax.oxy.remotely.network.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlConfigurationAdapter implements NetworkConfigurationAdapter {
    @Override
    public String read(String content, String key) {
        List<String> lines = lines(content);
        String[] segments = key.split("\\.");
        int start = 0;
        int end = lines.size();
        int indent = 0;
        for (int depth = 0; depth < segments.length; depth++) {
            int lineIndex = find(lines, segments[depth], start, end, indent);
            if (lineIndex < 0) {
                return "";
            }
            Matcher matcher = mappingPattern(segments[depth]).matcher(lines.get(lineIndex));
            matcher.matches();
            if (depth == segments.length - 1) {
                return decodeScalar(scalarWithoutComment(matcher.group(1)));
            }
            if (!scalarWithoutComment(matcher.group(1)).isBlank()) {
                return "";
            }
            Bounds child = childBounds(lines, lineIndex, end, indent);
            start = child.start();
            end = child.end();
            indent = child.indent();
        }
        return "";
    }

    @Override
    public boolean contains(String content, String key) {
        List<String> lines = lines(content);
        String[] segments = key.split("\\.");
        int start = 0;
        int end = lines.size();
        int indent = 0;
        for (int depth = 0; depth < segments.length; depth++) {
            int lineIndex = find(lines, segments[depth], start, end, indent);
            if (lineIndex < 0) {
                return false;
            }
            if (depth == segments.length - 1) {
                return true;
            }
            Matcher matcher = mappingPattern(segments[depth]).matcher(lines.get(lineIndex));
            matcher.matches();
            if (!scalarWithoutComment(matcher.group(1)).isBlank()) {
                return false;
            }
            Bounds child = childBounds(lines, lineIndex, end, indent);
            start = child.start();
            end = child.end();
            indent = child.indent();
        }
        return false;
    }

    @Override
    public String apply(String content, String key, String value) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        String[] segments = key.split("\\.");
        apply(lines, segments, 0, 0, lines.size(), 0, formatScalar(value));
        return String.join(ending, lines);
    }

    @Override
    public String remove(String content, String key) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        String[] segments = key.split("\\.");
        int start = 0;
        int end = lines.size();
        int indent = 0;
        for (int depth = 0; depth < segments.length; depth++) {
            int lineIndex = find(lines, segments[depth], start, end, indent);
            if (lineIndex < 0) {
                return content == null ? "" : content;
            }
            Matcher matcher = mappingPattern(segments[depth]).matcher(lines.get(lineIndex));
            matcher.matches();
            if (depth == segments.length - 1) {
                lines.remove(lineIndex);
                return String.join(ending, lines);
            }
            if (!scalarWithoutComment(matcher.group(1)).isBlank()) {
                throw new IllegalArgumentException("YAML path " + key + " crosses a scalar at " + segments[depth]);
            }
            Bounds child = childBounds(lines, lineIndex, end, indent);
            start = child.start();
            end = child.end();
            indent = child.indent();
        }
        return String.join(ending, lines);
    }

    private void apply(List<String> lines, String[] segments, int depth, int start, int end, int indent, String value) {
        int lineIndex = find(lines, segments[depth], start, end, indent);
        if (lineIndex < 0) {
            int insertAt = insertionIndex(lines, start, end);
            List<String> addition = new ArrayList<>();
            for (int remaining = depth; remaining < segments.length; remaining++) {
                int currentIndent = indent + (remaining - depth) * 2;
                String suffix = remaining == segments.length - 1 ? ": " + value : ":";
                addition.add(" ".repeat(currentIndent) + segments[remaining] + suffix);
            }
            lines.addAll(insertAt, addition);
            return;
        }
        Matcher matcher = mappingPattern(segments[depth]).matcher(lines.get(lineIndex));
        matcher.matches();
        if (depth == segments.length - 1) {
            String suffix = commentSuffix(matcher.group(1));
            lines.set(lineIndex, " ".repeat(indent) + segments[depth] + ": " + value + suffix);
            return;
        }
        if (!scalarWithoutComment(matcher.group(1)).isBlank()) {
            throw new IllegalArgumentException("YAML path " + String.join(".", segments) + " crosses a scalar at " + segments[depth]);
        }
        Bounds child = childBounds(lines, lineIndex, end, indent);
        apply(lines, segments, depth + 1, child.start(), child.end(), child.indent(), value);
    }

    private Bounds childBounds(List<String> lines, int parentIndex, int parentEnd, int parentIndent) {
        int start = parentIndex + 1;
        int end = parentEnd;
        int indent = parentIndent + 2;
        for (int index = start; index < parentEnd; index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            int lineIndent = indentation(line);
            if (lineIndent <= parentIndent) {
                end = index;
                break;
            }
            indent = lineIndent;
            break;
        }
        return new Bounds(start, end, indent);
    }

    private int find(List<String> lines, String key, int start, int end, int indent) {
        Pattern pattern = mappingPattern(key);
        for (int index = start; index < end; index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#") || indentation(line) != indent) {
                continue;
            }
            if (pattern.matcher(line).matches()) {
                return index;
            }
        }
        return -1;
    }

    private Pattern mappingPattern(String key) {
        return Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.*)$");
    }

    private int insertionIndex(List<String> lines, int start, int end) {
        int index = end;
        while (index > start && lines.get(index - 1).isBlank()) {
            index--;
        }
        return index;
    }

    private int indentation(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private String scalarWithoutComment(String value) {
        String trimmed = value == null ? "" : value.trim();
        int comment = commentIndex(trimmed);
        return (comment < 0 ? trimmed : trimmed.substring(0, comment)).trim();
    }

    private String commentSuffix(String value) {
        String trimmed = value == null ? "" : value.trim();
        int comment = commentIndex(trimmed);
        return comment < 0 ? "" : " " + trimmed.substring(comment).trim();
    }

    private int commentIndex(String value) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\'' && !doubleQuoted) singleQuoted = !singleQuoted;
            if (character == '"' && !singleQuoted && (index == 0 || value.charAt(index - 1) != '\\')) doubleQuoted = !doubleQuoted;
            if (character == '#' && !singleQuoted && !doubleQuoted && (index == 0 || Character.isWhitespace(value.charAt(index - 1)))) return index;
        }
        return -1;
    }

    private String formatScalar(String value) {
        String resolved = value == null ? "" : value;
        if (resolved.matches("(?i:true|false|null|~|-?\\d+(?:\\.\\d+)?)") || resolved.startsWith("[") || resolved.startsWith("{") || resolved.startsWith("\"") || resolved.startsWith("'")) {
            return resolved;
        }
        return "'" + resolved.replace("'", "''") + "'";
    }

    private String decodeScalar(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private List<String> lines(String content) {
        return Arrays.asList((content == null ? "" : content).split("\\R", -1));
    }

    private String lineEnding(String content) {
        return content != null && content.contains("\r\n") ? "\r\n" : "\n";
    }

    private record Bounds(int start, int end, int indent) {
    }
}
