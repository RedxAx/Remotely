package redxax.oxy.remotely.data.item;

import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.flow.data.Visual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record UiItem(String id, int count, String name, List<String> lore, Integer modelData, Map<String, Object> tag) {
    public UiItem {
        lore = lore != null ? List.copyOf(lore) : List.of();
        tag = tag != null ? Map.copyOf(tag) : Map.of();
    }

    public static UiItem of(String id, int count) {
        String normalized = normalizeId(id);
        if (normalized == null) return null;
        return new UiItem(normalized, count, null, List.of(), null, Map.of());
    }

    public static UiItem fromPlayerItem(PlayerItem item) {
        if (item == null) return null;
        String normalized = normalizeId(item.id());
        if (normalized == null || normalized.isBlank() || normalized.endsWith(":air")) return null;
        Map<String, Object> tag = item.tag() != null ? item.tag() : Map.of();
        String name = readName(tag);
        List<String> lore = readLore(tag);
        Integer modelData = readModelData(tag);
        return new UiItem(normalized, item.count(), name, lore, modelData, tag);
    }

    public static UiItem fromVisual(Visual visual) {
        if (visual == null) return null;
        String normalized = normalizeId(visual.getMaterial());
        if (normalized == null) return null;
        String name = visual.getName();
        List<String> lore = normalizeLore(visual.getLore());
        Integer modelData = visual.getModelData();
        return new UiItem(normalized, 1, name, lore, modelData, Map.of());
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isBlank()) return null;
        String namespace = "minecraft";
        String path = trimmed;
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":", 2);
            if (parts.length > 0 && !parts[0].isBlank()) {
                namespace = parts[0].trim();
            }
            path = parts.length > 1 ? parts[1] : "";
        }
        namespace = namespace.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (path.isBlank()) return null;
        return namespace + ":" + path;
    }

    private static String readName(Map<String, Object> tag) {
        Map<String, Object> display = getMap(tag, "display");
        String name = getString(display, "Name");
        if (name == null) name = getString(display, "name");
        if (name != null) return name;
        Map<String, Object> components = getMap(tag, "components");
        name = getString(components, "minecraft:custom_name");
        if (name == null) name = getString(components, "custom_name");
        return name;
    }

    private static List<String> readLore(Map<String, Object> tag) {
        Map<String, Object> display = getMap(tag, "display");
        List<String> lore = toStringList(display.get("Lore"));
        if (lore.isEmpty()) {
            lore = toStringList(display.get("lore"));
        }
        if (!lore.isEmpty()) return lore;
        Map<String, Object> components = getMap(tag, "components");
        lore = toStringList(components.get("minecraft:lore"));
        if (lore.isEmpty()) {
            lore = toStringList(components.get("lore"));
        }
        return lore;
    }

    private static Integer readModelData(Map<String, Object> tag) {
        Integer model = getInt(tag, "CustomModelData");
        if (model == null) model = getInt(tag, "custom_model_data");
        if (model != null) return model;
        Map<String, Object> components = getMap(tag, "components");
        model = getInt(components, "minecraft:custom_model_data");
        if (model == null) model = getInt(components, "custom_model_data");
        return model;
    }

    private static List<String> normalizeLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static Map<String, Object> getMap(Map<String, Object> root, String key) {
        if (root == null || key == null) return Collections.emptyMap();
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                if (k != null) {
                    out.put(String.valueOf(k), entry.getValue());
                }
            }
            return out;
        }
        return Collections.emptyMap();
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object value = map.get(key);
        return toStringValue(value);
    }

    private static String toStringValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String s) return s;
        }
        return String.valueOf(value);
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object value = map.get(key);
        return toInt(value);
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Object v = map.get("value");
            if (v == null) v = map.get("model");
            if (v == null) v = map.get("data");
            return toInt(v);
        }
        return null;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object entry : list) {
            String text = toStringValue(entry);
            if (text != null && !text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }
}
