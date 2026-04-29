package redxax.oxy.remotely.worldgen.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class WorldGenSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String serialize(WorldGenGraph graph) {
        return GSON.toJson(graph);
    }

    public static WorldGenGraph deserialize(String json) {
        return GSON.fromJson(json, WorldGenGraph.class);
    }
}
