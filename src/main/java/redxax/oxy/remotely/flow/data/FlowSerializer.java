package redxax.oxy.remotely.flow.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FlowSerializer {
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static String serialize(FlowGraph graph) {
        return gson.toJson(graph);
    }

    public static FlowGraph deserialize(String json) {
        return gson.fromJson(json, FlowGraph.class);
    }

    public static String serializeGui(GuiDefinition gui) {
        return gson.toJson(gui);
    }

    public static GuiDefinition deserializeGui(String json) {
        return gson.fromJson(json, GuiDefinition.class);
    }
}
