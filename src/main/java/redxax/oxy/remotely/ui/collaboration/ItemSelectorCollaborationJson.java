package redxax.oxy.remotely.ui.collaboration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;

import java.util.ArrayList;
import java.util.List;

public final class ItemSelectorCollaborationJson {
    private ItemSelectorCollaborationJson() {
    }

    public static JsonObject write(ItemSelectorWidget.CollaborationState value) {
        JsonObject result = new JsonObject();
        result.addProperty("width", value.width());
        result.addProperty("height", value.height());
        result.addProperty("query", value.query());
        result.addProperty("selectedItem", value.selectedItem());
        result.addProperty("emptyMessage", value.emptyMessage());
        result.addProperty("loading", value.loading());
        result.addProperty("scrollOffset", value.scrollOffset());
        result.addProperty("entryHeight", value.entryHeight());
        JsonArray items = new JsonArray();
        value.items().forEach(item -> {
            JsonObject object = new JsonObject();
            object.addProperty("label", item.label());
            object.addProperty("iconNamespace", item.iconNamespace());
            object.addProperty("iconPath", item.iconPath());
            object.addProperty("iconType", item.iconType());
            object.addProperty("hint", item.hint());
            object.addProperty("searchTerms", item.searchTerms());
            object.addProperty("rankingPriority", item.rankingPriority());
            object.addProperty("badge", item.badge());
            object.addProperty("section", item.section());
            items.add(object);
        });
        result.add("items", items);
        return result;
    }

    public static ItemSelectorWidget.CollaborationState read(JsonElement value) {
        if (value == null || !value.isJsonObject()) return null;
        JsonObject object = value.getAsJsonObject();
        List<ItemSelectorWidget.CollaborationItem> items = new ArrayList<>();
        JsonElement itemElement = object.get("items");
        if (itemElement != null && itemElement.isJsonArray()) {
            itemElement.getAsJsonArray().forEach(item -> {
                if (!item.isJsonObject()) return;
                JsonObject data = item.getAsJsonObject();
                items.add(new ItemSelectorWidget.CollaborationItem(text(data, "label"), text(data, "iconNamespace"), text(data, "iconPath"),
                        text(data, "iconType"), text(data, "hint"), text(data, "searchTerms"), integer(data, "rankingPriority", 0),
                        text(data, "badge"), bool(data, "section", false)));
            });
        }
        return new ItemSelectorWidget.CollaborationState(integer(object, "width", 1), integer(object, "height", 1), text(object, "query"),
                text(object, "selectedItem"), text(object, "emptyMessage"), bool(object, "loading", false), decimal(object, "scrollOffset", 0),
                integer(object, "entryHeight", 1), List.copyOf(items));
    }

    private static String text(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int integer(JsonObject value, String name, int fallback) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private static float decimal(JsonObject value, String name, float fallback) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static boolean bool(JsonObject value, String name, boolean fallback) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }
}
