package redxax.oxy.remotely.data.managed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import restudio.rescreen.util.JsonTreeParser;

import java.util.ArrayList;
import java.util.List;

public final class PlayerActionJson {
    private PlayerActionJson() {
    }

    public static List<PlayerAction> read(String value) {
        if (value == null || value.isBlank()) return List.of();
        JsonElement root = JsonTreeParser.parse(value);
        if (!root.isJsonArray()) return List.of();
        List<PlayerAction> result = new ArrayList<>();
        root.getAsJsonArray().forEach(element -> {
            if (!element.isJsonObject()) return;
            JsonObject object = element.getAsJsonObject();
            PlayerAction action = new PlayerAction(text(object, "name"), text(object, "icon"), text(object, "command"));
            action.uuid = text(object, "uuid");
            result.add(action);
        });
        return List.copyOf(result);
    }

    public static String write(List<PlayerAction> actions) {
        JsonArray root = new JsonArray();
        if (actions != null) {
            actions.forEach(action -> {
                if (action == null) return;
                JsonObject object = new JsonObject();
                object.addProperty("uuid", action.uuid);
                object.addProperty("name", action.name);
                object.addProperty("icon", action.icon);
                object.addProperty("command", action.command);
                root.add(object);
            });
        }
        return JsonTreeParser.write(root);
    }

    private static String text(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
