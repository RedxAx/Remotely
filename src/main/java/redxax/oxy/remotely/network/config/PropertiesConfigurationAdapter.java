package redxax.oxy.remotely.network.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertiesConfigurationAdapter implements NetworkConfigurationAdapter {
    @Override
    public String read(String content, String key) {
        Pattern pattern = keyPattern(key);
        for (String line : lines(content)) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher.group(2).trim();
            }
        }
        return "";
    }

    @Override
    public boolean contains(String content, String key) {
        Pattern pattern = keyPattern(key);
        return lines(content).stream().anyMatch(line -> pattern.matcher(line).matches());
    }

    @Override
    public String apply(String content, String key, String value) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        Pattern pattern = keyPattern(key);
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = pattern.matcher(lines.get(index));
            if (matcher.matches()) {
                lines.set(index, matcher.group(1) + key + "=" + value);
                return join(lines, ending);
            }
        }
        trimTrailingEmpty(lines);
        lines.add(key + "=" + value);
        return join(lines, ending) + ending;
    }

    @Override
    public String remove(String content, String key) {
        String ending = lineEnding(content);
        List<String> lines = new ArrayList<>(lines(content));
        Pattern pattern = keyPattern(key);
        lines.removeIf(line -> pattern.matcher(line).matches());
        return join(lines, ending);
    }

    private Pattern keyPattern(String key) {
        return Pattern.compile("^(\\s*)" + Pattern.quote(key) + "\\s*[:=]\\s*(.*)$");
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
}
