package redxax.oxy.remotely.flow.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FlowSerializer {
    private static final Set<String> GRAPH_PROPERTIES = Set.of("id", "enabled", "version", "nodes", "connections", "localVariables", "function", "functionOwner", "functionNamespace", "functionVersion", "functionDescription", "functionInputs", "functionOutputs", "editorPassthroughs", "contentProperties", "resourceType", "resourceRevision", "resourceHash", "resourceMutationId", "assetFormatVersion", "assetRevision", "assetHash", "assetMutationId");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
            .create();

    public static String serialize(FlowGraph graph) {
        JsonObject object = toJsonObject(graph);
        return gson.toJson(object);
    }

    public static JsonObject toJsonObject(FlowGraph graph) {
        JsonObject object = gson.toJsonTree(graph).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : graph.getOpaqueProperties().entrySet()) {
            if (!object.has(entry.getKey()) && entry.getValue() != null) {
                object.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return object;
    }

    public static FlowGraph deserialize(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return deserialize(object);
    }

    public static FlowGraph deserialize(JsonObject object) {
        FlowGraph graph = gson.fromJson(object, FlowGraph.class);
        Map<String, JsonElement> opaque = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!GRAPH_PROPERTIES.contains(entry.getKey())) {
                opaque.put(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        graph.setOpaqueProperties(opaque);
        return graph;
    }

    public static FlowGraph deserializeFlow(String json) {
        return gson.fromJson(json, FlowGraph.class);
    }

    public static String serializeGui(GuiDefinition gui) {
        return gson.toJson(gui);
    }

    public static GuiDefinition deserializeGui(String json) {
        return gson.fromJson(json, GuiDefinition.class);
    }

    public static String serializeScoreboard(ScoreboardDefinition scoreboard) {
        return gson.toJson(scoreboard);
    }

    public static ScoreboardDefinition deserializeScoreboard(String json) {
        return gson.fromJson(json, ScoreboardDefinition.class);
    }

    public static String serializeTab(TabDefinition tab) {
        return gson.toJson(tab);
    }

    public static TabDefinition deserializeTab(String json) {
        return gson.fromJson(json, TabDefinition.class);
    }

    public static String serializeCustomContent(CustomContentDefinition content) {
        return gson.toJson(content);
    }

    public static CustomContentDefinition deserializeCustomContent(String json) {
        return gson.fromJson(json, CustomContentDefinition.class);
    }

}
