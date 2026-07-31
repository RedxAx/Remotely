package redxax.oxy.remotely.flow.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import restudio.resync.flow.workspace.WorkspacePatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class FlowWorkspaceDocument {
    private static final int MAX_PATCHES = 512;
    private static final Set<String> SERVER_MANAGED_ROOTS = Set.of(
        "id", "worldName", "flowId", "function", "resourceType", "resourceRevision", "resourceHash",
        "resourceMutationId", "enabled");

    private FlowWorkspaceDocument() {
    }

    public static JsonObject fromGraph(FlowGraph graph) {
        return JsonParser.parseString(FlowSerializer.serialize(graph)).getAsJsonObject();
    }

    public static List<WorkspacePatch<JsonElement>> diff(JsonObject before, JsonObject after) {
        ArrayList<WorkspacePatch<JsonElement>> patches = new ArrayList<>();
        diff("", before, after, patches);
        if (patches.size() <= MAX_PATCHES) {
            return List.copyOf(patches);
        }
        patches.clear();
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            JsonElement previous = before.get(key);
            JsonElement next = after.get(key);
            if (equals(previous, next)) {
                continue;
            }
            String path = "/" + escape(key);
            patches.add(next == null ? new WorkspacePatch<>("remove", path, JsonNull.INSTANCE)
                : new WorkspacePatch<>("set", path, next.deepCopy()));
        }
        return List.copyOf(patches);
    }

    public static JsonObject editableWorkspace(JsonObject document) {
        JsonObject editable = document != null ? document.deepCopy() : new JsonObject();
        SERVER_MANAGED_ROOTS.forEach(editable::remove);
        return editable;
    }

    public static List<WorkspacePatch<JsonElement>> diffEditableWorkspace(JsonObject before, JsonObject after) {
        return diff(editableWorkspace(before), editableWorkspace(after));
    }

    public static void apply(JsonObject document, List<WorkspacePatch<JsonElement>> patches) {
        if (document == null || patches == null) {
            return;
        }
        for (WorkspacePatch<JsonElement> patch : patches) {
            apply(document, patch);
        }
    }

    private static void diff(String path, JsonElement before, JsonElement after, List<WorkspacePatch<JsonElement>> patches) {
        if (equals(before, after)) {
            return;
        }
        if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
            Set<String> keys = new TreeSet<>();
            keys.addAll(before.getAsJsonObject().keySet());
            keys.addAll(after.getAsJsonObject().keySet());
            for (String key : keys) {
                diff(path + "/" + escape(key), before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), patches);
            }
            return;
        }
        if (before != null && after != null && before.isJsonArray() && after.isJsonArray() && "/connections".equals(path)) {
            diffConnections(path, before.getAsJsonArray(), after.getAsJsonArray(), patches);
            return;
        }
        if (after == null) {
            patches.add(new WorkspacePatch<>("remove", path, JsonNull.INSTANCE));
        } else {
            patches.add(new WorkspacePatch<>("set", path, after.deepCopy()));
        }
    }

    private static void diffConnections(String path, JsonArray before, JsonArray after, List<WorkspacePatch<JsonElement>> patches) {
        ArrayList<JsonElement> remaining = new ArrayList<>();
        before.forEach(value -> remaining.add(value.deepCopy()));
        for (JsonElement value : after) {
            int index = indexOf(remaining, value);
            if (index >= 0) {
                remaining.remove(index);
            } else {
                patches.add(new WorkspacePatch<>("array_add", path, value.deepCopy()));
            }
        }
        for (JsonElement value : remaining) {
            patches.add(new WorkspacePatch<>("array_remove", path, value));
        }
    }

    private static void apply(JsonObject document, WorkspacePatch<JsonElement> patch) {
        if (patch == null || patch.path() == null || !patch.path().startsWith("/")) {
            return;
        }
        List<String> segments = segments(patch.path());
        JsonElement parent = parent(document, segments);
        if (parent == null) {
            return;
        }
        String leaf = segments.getLast();
        switch (patch.op()) {
            case "set" -> set(parent, leaf, patch.value());
            case "remove" -> remove(parent, leaf);
            case "array_add" -> addArrayValue(array(parent, leaf), patch.value());
            case "array_remove" -> removeArrayValue(array(parent, leaf), patch.value());
        }
    }

    private static JsonElement parent(JsonObject document, List<String> path) {
        JsonElement current = document;
        for (int index = 0; index < path.size() - 1; index++) {
            String segment = path.get(index);
            if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                JsonElement next = object.get(segment);
                if (next == null || next.isJsonNull()) {
                    next = new JsonObject();
                    object.add(segment, next);
                }
                current = next;
            } else if (current.isJsonArray()) {
                int arrayIndex = Integer.parseInt(segment);
                if (arrayIndex < 0 || arrayIndex >= current.getAsJsonArray().size()) {
                    return null;
                }
                current = current.getAsJsonArray().get(arrayIndex);
            } else {
                return null;
            }
        }
        return current;
    }

    private static void set(JsonElement parent, String leaf, JsonElement value) {
        if (parent.isJsonObject()) {
            parent.getAsJsonObject().add(leaf, copy(value));
        } else if (parent.isJsonArray()) {
            parent.getAsJsonArray().set(Integer.parseInt(leaf), copy(value));
        }
    }

    private static void remove(JsonElement parent, String leaf) {
        if (parent.isJsonObject()) {
            parent.getAsJsonObject().remove(leaf);
        } else if (parent.isJsonArray()) {
            int index = Integer.parseInt(leaf);
            if (index >= 0 && index < parent.getAsJsonArray().size()) {
                parent.getAsJsonArray().remove(index);
            }
        }
    }

    private static JsonArray array(JsonElement parent, String leaf) {
        JsonElement value = parent.isJsonObject() ? parent.getAsJsonObject().get(leaf) : parent.getAsJsonArray().get(Integer.parseInt(leaf));
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static void addArrayValue(JsonArray array, JsonElement value) {
        if (array == null) {
            return;
        }
        JsonElement next = copy(value);
        for (JsonElement existing : array) {
            if (existing.equals(next)) {
                return;
            }
        }
        array.add(next);
    }

    private static void removeArrayValue(JsonArray array, JsonElement value) {
        if (array == null) {
            return;
        }
        for (int index = array.size() - 1; index >= 0; index--) {
            if (array.get(index).equals(value)) {
                array.remove(index);
            }
        }
    }

    private static int indexOf(List<JsonElement> values, JsonElement target) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equals(target)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean equals(JsonElement first, JsonElement second) {
        return first == second || first != null && first.equals(second);
    }

    private static List<String> segments(String path) {
        String[] raw = path.substring(1).split("/", -1);
        ArrayList<String> segments = new ArrayList<>(raw.length);
        for (String segment : raw) {
            segments.add(segment.replace("~1", "/").replace("~0", "~"));
        }
        return segments;
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static JsonElement copy(JsonElement value) {
        return value != null ? value.deepCopy() : JsonNull.INSTANCE;
    }
}
