package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import restudio.rescreen.util.JsonTreeParser;

import java.util.ArrayList;
import java.util.List;

public final class BrowserJson {
    private BrowserJson() {
    }

    public static JsonElement parse(String value) {
        return JsonTreeParser.parse(value);
    }

    public static JsonObject object(String value) {
        return object(parse(value));
    }

    public static JsonArray array(String value) {
        JsonElement element = parse(value);
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    public static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    public static String string(JsonObject value, String name) {
        return string(value, name, "");
    }

    public static String string(JsonObject value, String name, String fallback) {
        JsonElement element = element(value, name);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsString();
    }

    public static int integer(JsonObject value, String name, int fallback) {
        JsonElement element = element(value, name);
        try {
            return element == null ? fallback : element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static long longValue(JsonObject value, String name, long fallback) {
        JsonElement element = element(value, name);
        try {
            return element == null ? fallback : element.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static double decimal(JsonObject value, String name, double fallback) {
        JsonElement element = element(value, name);
        try {
            return element == null ? fallback : element.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject value, String name, boolean fallback) {
        JsonElement element = element(value, name);
        try {
            return element == null ? fallback : element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static JsonElement element(JsonObject value, String name) {
        if (value == null || name == null || !value.has(name)) return null;
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : element;
    }

    public static List<JsonObject> objects(JsonObject value, String name) {
        JsonElement element = element(value, name);
        if (element == null || !element.isJsonArray()) return List.of();
        List<JsonObject> result = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> {
            if (item != null && item.isJsonObject()) result.add(item.getAsJsonObject());
        });
        return List.copyOf(result);
    }

    public static List<String> strings(JsonObject value, String name) {
        JsonElement element = element(value, name);
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> {
            if (item != null && item.isJsonPrimitive()) result.add(item.getAsString());
        });
        return List.copyOf(result);
    }

    public static void put(JsonObject target, String name, String value) {
        if (value == null) target.add(name, JsonNull.INSTANCE);
        else target.addProperty(name, value);
    }

    public static void put(JsonObject target, String name, Number value) {
        if (value == null) target.add(name, JsonNull.INSTANCE);
        else target.addProperty(name, value);
    }

    public static void put(JsonObject target, String name, Boolean value) {
        if (value == null) target.add(name, JsonNull.INSTANCE);
        else target.addProperty(name, value);
    }

    public static void put(JsonObject target, String name, JsonElement value) {
        target.add(name, value == null ? JsonNull.INSTANCE : value);
    }

    public static String write(JsonElement value) {
        return JsonTreeParser.write(value);
    }
}
