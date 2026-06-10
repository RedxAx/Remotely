package redxax.oxy.remotely.flow.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class WorldUiSupport {
    private WorldUiSupport() {
    }

    static List<String> normalizeUniqueEntries(List<String> values) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(normalized.values());
    }

    static boolean hasAnyValue(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return true;
            }
        }
        return false;
    }

    static boolean isCompleteOrBlank(String... values) {
        if (values == null || values.length == 0) {
            return true;
        }
        int filled = 0;
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                filled++;
            }
        }
        return filled == 0 || filled == values.length;
    }

    static boolean areOrderedBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    public static boolean containsIgnoreCase(List<String> values, String value) {
        if (values == null || value == null) {
            return false;
        }
        for (String entry : values) {
            if (entry != null && entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidSimpleId(String value) {
        return value != null && value.matches("^[a-zA-Z0-9_\\-]+$");
    }

    static boolean isKnownEntryOrBlank(List<String> values, String value) {
        return value == null || value.trim().isBlank() || containsIgnoreCase(values, value.trim());
    }

    static List<String> mergeOptions(List<String> baseValues, String... extraValues) {
        List<String> merged = new ArrayList<>();
        if (baseValues != null) {
            merged.addAll(baseValues);
        }
        if (extraValues != null) {
            for (String value : extraValues) {
                if (value != null) {
                    merged.add(value);
                }
            }
        }
        return normalizeUniqueEntries(merged);
    }
}
