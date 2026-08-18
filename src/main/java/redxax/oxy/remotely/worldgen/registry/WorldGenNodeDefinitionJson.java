package redxax.oxy.remotely.worldgen.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowJson;

import java.util.ArrayList;
import java.util.List;

public final class WorldGenNodeDefinitionJson {
    public static List<WorldGenNodeDefinition> readList(JsonElement value) {
        List<WorldGenNodeDefinition> definitions = new ArrayList<>();
        if (value == null || !value.isJsonArray()) {
            return definitions;
        }
        for (JsonElement item : value.getAsJsonArray()) {
            if (item.isJsonObject()) {
                definitions.add(read(item.getAsJsonObject()));
            }
        }
        return definitions;
    }

    public static WorldGenNodeDefinition read(JsonObject json) {
        WorldGenNodeDefinition.Builder builder = WorldGenNodeDefinition.builder(text(json, "id", null), text(json, "displayName", null));
        if (json != null && json.has("category")) {
            builder.category(text(json, "category", null));
        }
        if (json != null && json.has("color")) {
            builder.color(integer(json, "color", 0));
        }
        if (json != null && json.has("priority")) {
            builder.priority(integer(json, "priority", 0));
        }
        if (json != null && json.has("description")) {
            builder.description(text(json, "description", null));
        }
        if (json != null && json.has("hidden")) {
            builder.hidden(bool(json, "hidden", false));
        }
        for (JsonElement item : array(json, "inputs")) {
            if (item.isJsonObject()) {
                JsonObject pin = item.getAsJsonObject();
                builder.input(text(pin, "name", null), dataType(pin), value(pin, "defaultValue"), text(pin, "widgetType", null),
                    strings(pin.get("options")), text(pin, "description", null));
            }
        }
        for (JsonElement item : array(json, "outputs")) {
            if (item.isJsonObject()) {
                JsonObject pin = item.getAsJsonObject();
                builder.output(text(pin, "name", null), dataType(pin));
            }
        }
        return builder.build();
    }

    private static FlowDataType dataType(JsonObject json) {
        if (json == null || !json.has("dataType") || json.get("dataType").isJsonNull()) {
            return null;
        }
        return FlowDataType.fromString(text(json, "dataType", "any"));
    }

    private static Object value(JsonObject json, String key) {
        return FlowJson.value(json == null ? null : json.get(key));
    }

    private static List<String> strings(JsonElement value) {
        List<String> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) {
            return result;
        }
        for (JsonElement item : value.getAsJsonArray()) {
            result.add(text(item, null));
        }
        return result;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        return text(json.get(key), null);
    }

    private static String text(JsonElement value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement value = json == null ? null : json.get(key);
        if (value == null || value.isJsonNull()) {
            return json != null && json.has(key) ? 0 : fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json == null ? null : json.get(key);
        if (value == null || value.isJsonNull()) {
            return json != null && json.has(key) ? false : fallback;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private WorldGenNodeDefinitionJson() {
    }
}
