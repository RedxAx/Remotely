package redxax.oxy.remotely.packcontent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NexoGlyphConfig {
    private NexoGlyphConfig() {
    }

    public static Map<String, Map<String, Object>> parse(String content) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) return result;
        String currentGlyph = null;
        String currentList = null;
        for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = indentation(line);
            if (indent == 0) {
                currentList = null;
                currentGlyph = trimmed.endsWith(":") ? unquote(trimmed.substring(0, trimmed.length() - 1).trim()) : null;
                if (currentGlyph != null && !currentGlyph.isBlank()) result.putIfAbsent(currentGlyph, new LinkedHashMap<>());
                continue;
            }
            if (currentGlyph == null) continue;
            if (trimmed.startsWith("-") && currentList != null) {
                Object value = result.get(currentGlyph).get(currentList);
                if (value instanceof List<?> values) {
                    @SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) values;
                    mutable.add(scalar(trimmed.substring(1).trim()));
                }
                continue;
            }
            int colon = keyColon(trimmed);
            if (colon <= 0) {
                currentList = null;
                continue;
            }
            String key = unquote(trimmed.substring(0, colon).trim());
            String value = stripComment(trimmed.substring(colon + 1).trim());
            if (value.isEmpty()) {
                List<Object> list = new ArrayList<>();
                result.get(currentGlyph).put(key, list);
                currentList = key;
            } else {
                result.get(currentGlyph).put(key, scalar(value));
                currentList = null;
            }
        }
        return result;
    }

    private static Object scalar(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            List<Object> values = new ArrayList<>();
            for (String entry : normalized.substring(1, normalized.length() - 1).split(",")) {
                if (!entry.isBlank()) values.add(scalar(entry));
            }
            return values;
        }
        String unquoted = unquote(normalized);
        if (unquoted.matches("-?\\d+")) {
            try {
                return Integer.parseInt(unquoted);
            } catch (NumberFormatException ignored) {
                return unquoted;
            }
        }
        return unquoted;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int indentation(String line) {
        int result = 0;
        while (result < line.length() && (line.charAt(result) == ' ' || line.charAt(result) == '\t')) result++;
        return result;
    }

    private static int keyColon(String value) {
        boolean single = false;
        boolean doubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\'' && !doubleQuote) single = !single;
            else if (character == '"' && !single) doubleQuote = !doubleQuote;
            else if (character == ':' && !single && !doubleQuote) return i;
        }
        return -1;
    }

    private static String stripComment(String value) {
        boolean single = false;
        boolean doubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\'' && !doubleQuote) single = !single;
            else if (character == '"' && !single) doubleQuote = !doubleQuote;
            else if (character == '#' && !single && !doubleQuote && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))) {
                return value.substring(0, i).trim();
            }
        }
        return value;
    }
}
