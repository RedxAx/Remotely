package redxax.oxy.remotely.flow.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

public class FlowSerializer {
    private static final Set<String> GRAPH_PROPERTIES = Set.of("id", "enabled", "version", "nodes", "connections", "localVariables", "function", "functionOwner", "functionNamespace", "functionVersion", "functionDescription", "functionInputs", "functionOutputs", "editorPassthroughs", "contentProperties", "resourceType", "resourceRevision", "resourceHash", "resourceMutationId", "assetFormatVersion", "assetRevision", "assetHash", "assetMutationId");
    public static String serialize(FlowGraph graph) {
        return FlowJson.write(toJsonObject(graph));
    }

    public static JsonObject toJsonObject(FlowGraph graph) {
        return FlowJson.graph(graph);
    }

    public static FlowGraph deserialize(String json) {
        return deserialize(FlowJson.parse(json).getAsJsonObject());
    }

    public static FlowGraph deserialize(JsonObject object) {
        return FlowJson.graph(object);
    }

    public static FlowGraph deserialize(JsonElement value) {
        return deserialize(value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject());
    }

    public static FlowGraph deserializeFlow(String json) {
        return deserialize(json);
    }

    public static String serializeGui(GuiDefinition gui) {
        return FlowJson.write(FlowJson.gui(gui));
    }

    public static GuiDefinition deserializeGui(String json) {
        return FlowJson.gui(FlowJson.parse(json).getAsJsonObject());
    }

    public static GuiDefinition deserializeGui(JsonElement value) {
        return FlowJson.gui(value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject());
    }

    public static String serializeScoreboard(ScoreboardDefinition scoreboard) {
        return FlowJson.write(FlowJson.scoreboard(scoreboard));
    }

    public static ScoreboardDefinition deserializeScoreboard(String json) {
        return FlowJson.scoreboard(FlowJson.parse(json).getAsJsonObject());
    }

    public static ScoreboardDefinition deserializeScoreboard(JsonElement value) {
        return FlowJson.scoreboard(value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject());
    }

    public static String serializeTab(TabDefinition tab) {
        return FlowJson.write(FlowJson.tab(tab));
    }

    public static TabDefinition deserializeTab(String json) {
        return FlowJson.tab(FlowJson.parse(json).getAsJsonObject());
    }

    public static TabDefinition deserializeTab(JsonElement value) {
        return FlowJson.tab(value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject());
    }

    public static String serializeCustomContent(CustomContentDefinition content) {
        return FlowJson.write(FlowJson.customContent(content));
    }

    public static CustomContentDefinition deserializeCustomContent(String json) {
        return FlowJson.customContent(FlowJson.parse(json).getAsJsonObject());
    }

    public static CustomContentDefinition deserializeCustomContent(JsonElement value) {
        return FlowJson.customContent(value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject());
    }

    static Set<String> graphProperties() {
        return GRAPH_PROPERTIES;
    }

}
