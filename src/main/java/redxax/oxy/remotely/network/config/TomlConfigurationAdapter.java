package redxax.oxy.remotely.network.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomlConfigurationAdapter implements NetworkConfigurationAdapter {
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]\\s*(?:#.*)?$");
    private static final Pattern ASSIGNMENT = Pattern.compile("^\\s*(?:[A-Za-z0-9_-]+|\"(?:\\\\.|[^\"])*\")\\s*=\\s*([^#]*?)(\\s+#.*)?$");

    @Override
    public String read(String content, String key) {
        KeyLocation location = locate(key);
        List<String> lines = lines(content);
        Bounds bounds = bounds(lines, location.section());
        if (location.key().equals("*")) {
            return "";
        }
        Pattern keyPattern = keyPattern(location.key());
        for (int index = bounds.start(); index < bounds.end(); index++) {
            Matcher matcher = keyPattern.matcher(lines.get(index));
            if (matcher.matches()) {
                int valueEnd = valueEnd(lines, index, bounds.end(), matcher.group(2));
                StringBuilder value = new StringBuilder(matcher.group(2).trim());
                for (int continuation = index + 1; continuation <= valueEnd; continuation++) {
                    value.append(' ').append(lines.get(continuation).trim());
                }
                return value.toString();
            }
        }
        return "";
    }

    @Override
    public boolean contains(String content, String key) {
        KeyLocation location = locate(key);
        List<String> lines = lines(content);
        Bounds bounds = bounds(lines, location.section());
        if (location.key().equals("*")) {
            return !bounds.exists() || lines.subList(bounds.start(), bounds.end()).stream().anyMatch(line -> ASSIGNMENT.matcher(line).matches());
        }
        Pattern keyPattern = keyPattern(location.key());
        for (int index = bounds.start(); index < bounds.end(); index++) {
            if (keyPattern.matcher(lines.get(index)).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String apply(String content, String key, String value) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        KeyLocation location = locate(key);
        Bounds bounds = bounds(lines, location.section());
        if (location.key().equals("*")) {
            removeSectionAssignments(lines, bounds);
            ensureSection(lines, location.section(), bounds.exists());
            return join(lines, ending);
        }
        Pattern keyPattern = keyPattern(location.key());
        String renderedKey = renderKey(location.key());
        for (int index = bounds.start(); index < bounds.end(); index++) {
            Matcher matcher = keyPattern.matcher(lines.get(index));
            if (matcher.matches()) {
                String suffix = matcher.group(3) == null ? "" : matcher.group(3);
                int valueEnd = valueEnd(lines, index, bounds.end(), matcher.group(2));
                for (int continuation = valueEnd; continuation > index; continuation--) {
                    lines.remove(continuation);
                }
                lines.set(index, matcher.group(1) + renderedKey + " = " + value + suffix);
                return join(lines, ending);
            }
        }
        if (location.section().isBlank()) {
            int insertAt = firstSection(lines);
            lines.add(insertAt, renderedKey + " = " + value);
            return join(lines, ending);
        }
        if (bounds.exists()) {
            lines.add(bounds.end(), renderedKey + " = " + value);
            return join(lines, ending);
        }
        trimTrailingEmpty(lines);
        if (!lines.isEmpty()) {
            lines.add("");
        }
        lines.add("[" + location.section() + "]");
        lines.add(renderedKey + " = " + value);
        return join(lines, ending) + ending;
    }

    @Override
    public String remove(String content, String key) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        KeyLocation location = locate(key);
        Bounds bounds = bounds(lines, location.section());
        if (location.key().equals("*")) {
            removeSectionAssignments(lines, bounds);
            ensureSection(lines, location.section(), bounds.exists());
            return join(lines, ending);
        }
        Pattern keyPattern = keyPattern(location.key());
        for (int index = bounds.start(); index < bounds.end(); index++) {
            Matcher matcher = keyPattern.matcher(lines.get(index));
            if (matcher.matches()) {
                int valueEnd = valueEnd(lines, index, bounds.end(), matcher.group(2));
                for (int removeIndex = valueEnd; removeIndex >= index; removeIndex--) {
                    lines.remove(removeIndex);
                }
                return join(lines, ending);
            }
        }
        return join(lines, ending);
    }

    private void removeSectionAssignments(List<String> lines, Bounds bounds) {
        List<int[]> ranges = new ArrayList<>();
        for (int index = bounds.start(); index < bounds.end(); index++) {
            Matcher matcher = ASSIGNMENT.matcher(lines.get(index));
            if (!matcher.matches()) {
                continue;
            }
            int valueEnd = valueEnd(lines, index, bounds.end(), matcher.group(1));
            ranges.add(new int[]{index, valueEnd});
            index = valueEnd;
        }
        for (int rangeIndex = ranges.size() - 1; rangeIndex >= 0; rangeIndex--) {
            int[] range = ranges.get(rangeIndex);
            for (int removeIndex = range[1]; removeIndex >= range[0]; removeIndex--) {
                lines.remove(removeIndex);
            }
        }
    }

    private void ensureSection(List<String> lines, String section, boolean exists) {
        if (exists || section.isBlank()) return;
        trimTrailingEmpty(lines);
        if (!lines.isEmpty()) lines.add("");
        lines.add("[" + section + "]");
        lines.add("");
    }

    private int valueEnd(List<String> lines, int start, int boundEnd, String firstValue) {
        int depth = arrayDepth(firstValue);
        int end = start;
        while (depth > 0 && ++end < boundEnd) {
            depth += arrayDepth(lines.get(end));
        }
        return Math.min(end, boundEnd - 1);
    }

    private int arrayDepth(String value) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (char character : value.toCharArray()) {
            if (escaped) {
                escaped = false;
            } else if (character == '\\' && quoted) {
                escaped = true;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && character == '#') {
                break;
            } else if (!quoted && character == '[') {
                depth++;
            } else if (!quoted && character == ']') {
                depth--;
            }
        }
        return depth;
    }

    private Bounds bounds(List<String> lines, String section) {
        if (section.isBlank()) {
            return new Bounds(0, firstSection(lines), true);
        }
        int start = -1;
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = SECTION.matcher(lines.get(index));
            if (!matcher.matches()) {
                continue;
            }
            if (start >= 0) {
                return new Bounds(start, index, true);
            }
            if (unquote(matcher.group(1).trim()).equals(section)) {
                start = index + 1;
            }
        }
        return start >= 0 ? new Bounds(start, lines.size(), true) : new Bounds(lines.size(), lines.size(), false);
    }

    private int firstSection(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (SECTION.matcher(lines.get(index)).matches()) {
                return index;
            }
        }
        return lines.size();
    }

    private Pattern keyPattern(String key) {
        String bare = Pattern.quote(key);
        String quoted = Pattern.quote("\"" + key.replace("\"", "\\\"") + "\"");
        return Pattern.compile("^(\\s*)(?:" + bare + "|" + quoted + ")\\s*=\\s*([^#]*?)(\\s+#.*)?$");
    }

    private KeyLocation locate(String key) {
        int separator = key.indexOf('.');
        if (separator < 0) {
            return new KeyLocation("", key);
        }
        return new KeyLocation(key.substring(0, separator), key.substring(separator + 1));
    }

    private String renderKey(String key) {
        if (key.matches("[A-Za-z0-9_-]+")) {
            return key;
        }
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private List<String> lines(String content) {
        return Arrays.asList((content == null ? "" : content).split("\\R", -1));
    }

    private String lineEnding(String content) {
        return content != null && content.contains("\r\n") ? "\r\n" : "\n";
    }

    private String join(List<String> lines, String ending) {
        return String.join(ending, lines);
    }

    private void trimTrailingEmpty(List<String> lines) {
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
    }

    private record KeyLocation(String section, String key) {
    }

    private record Bounds(int start, int end, boolean exists) {
    }
}
