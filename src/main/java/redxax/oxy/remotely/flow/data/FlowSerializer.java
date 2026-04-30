package redxax.oxy.remotely.flow.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FlowSerializer {
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
            .create();

    public static String serialize(FlowGraph graph) {
        return gson.toJson(graph);
    }

    public static FlowGraph deserialize(String json) {
        return gson.fromJson(json, FlowGraph.class);
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
