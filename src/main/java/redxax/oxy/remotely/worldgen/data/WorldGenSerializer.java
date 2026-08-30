package redxax.oxy.remotely.worldgen.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import redxax.oxy.remotely.flow.data.FlowJson;
import restudio.rescreen.util.JsonTreeParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldGenSerializer {
    public static String serialize(WorldGenGraph graph) {
        return JsonTreeParser.write(graph(graph));
    }

    public static WorldGenGraph deserialize(String json) {
        return graph(object(json));
    }

    public static String serializeProject(WorldGenProject project) {
        return JsonTreeParser.write(project(project));
    }

    public static WorldGenProject deserializeProject(String json) {
        return project(object(json));
    }

    private static JsonObject project(WorldGenProject value) {
        WorldGenProject project = value == null ? new WorldGenProject() : value;
        JsonObject json = new JsonObject();
        put(json, "id", project.getId());
        put(json, "version", project.getVersion());
        json.add("terrainGraph", graph(project.getTerrainGraph()));
        json.add("biomeGraph", graph(project.getBiomeGraph()));
        json.add("surfaceGraph", graph(project.getSurfaceGraph()));
        json.add("caveGraph", graph(project.getCaveGraph()));
        json.add("featureGraph", graph(project.getFeatureGraph()));
        json.add("structureGraph", graph(project.getStructureGraph()));
        json.add("spawnGraph", graph(project.getSpawnGraph()));
        json.add("settings", settings(project.getSettings()));
        JsonArray profiles = new JsonArray();
        project.getBiomeProfiles().forEach(profile -> profiles.add(profile(profile)));
        json.add("biomeProfiles", profiles);
        return json;
    }

    private static WorldGenProject project(JsonObject json) {
        WorldGenProject project = new WorldGenProject();
        project.setId(string(json, "id", project.getId()));
        project.setVersion(integer(json, "version", project.getVersion()));
        project.setTerrainGraph(graph(child(json, "terrainGraph")));
        project.setBiomeGraph(graph(child(json, "biomeGraph")));
        project.setSurfaceGraph(graph(child(json, "surfaceGraph")));
        project.setCaveGraph(graph(child(json, "caveGraph")));
        project.setFeatureGraph(graph(child(json, "featureGraph")));
        project.setStructureGraph(graph(child(json, "structureGraph")));
        project.setSpawnGraph(graph(child(json, "spawnGraph")));
        project.setSettings(settings(child(json, "settings")));
        List<WorldGenBiomeProfile> profiles = new ArrayList<>();
        array(json, "biomeProfiles").forEach(value -> {
            if (value.isJsonObject()) profiles.add(profile(value.getAsJsonObject()));
        });
        project.setBiomeProfiles(profiles);
        return project;
    }

    private static JsonObject graph(WorldGenGraph value) {
        WorldGenGraph graph = value == null ? new WorldGenGraph() : value;
        JsonObject json = new JsonObject();
        put(json, "id", graph.getId());
        put(json, "version", graph.getVersion());
        JsonObject nodes = new JsonObject();
        graph.getNodes().forEach((id, node) -> nodes.add(id, node(node)));
        json.add("nodes", nodes);
        JsonArray connections = new JsonArray();
        graph.getConnections().forEach(connection -> connections.add(connection(connection)));
        json.add("connections", connections);
        return json;
    }

    private static WorldGenGraph graph(JsonObject json) {
        WorldGenGraph graph = new WorldGenGraph();
        if (json == null) return graph;
        graph.setId(string(json, "id", graph.getId()));
        graph.setVersion(integer(json, "version", graph.getVersion()));
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        JsonObject encodedNodes = child(json, "nodes");
        if (encodedNodes != null) encodedNodes.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) nodes.put(entry.getKey(), node(entry.getValue().getAsJsonObject()));
        });
        graph.setNodes(nodes);
        List<WorldGenConnection> connections = new ArrayList<>();
        array(json, "connections").forEach(value -> {
            if (value.isJsonObject()) connections.add(connection(value.getAsJsonObject()));
        });
        graph.setConnections(connections);
        return graph;
    }

    private static JsonObject node(WorldGenNode node) {
        JsonObject json = new JsonObject();
        put(json, "type", node.getType());
        put(json, "x", node.getX());
        put(json, "y", node.getY());
        JsonObject inputs = new JsonObject();
        node.getInputValues().forEach((key, value) -> inputs.add(key, value(value)));
        json.add("inputValues", inputs);
        return json;
    }

    private static WorldGenNode node(JsonObject json) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        JsonObject encoded = child(json, "inputValues");
        if (encoded != null) encoded.entrySet().forEach(entry -> inputs.put(entry.getKey(), value(entry.getValue())));
        return new WorldGenNode(string(json, "type", ""), decimal(json, "x", 0.0), decimal(json, "y", 0.0), inputs);
    }

    private static JsonObject connection(WorldGenConnection value) {
        JsonObject json = new JsonObject();
        put(json, "sourceNodeId", value.getSourceNodeId());
        put(json, "sourcePin", value.getSourcePin());
        put(json, "targetNodeId", value.getTargetNodeId());
        put(json, "targetPin", value.getTargetPin());
        return json;
    }

    private static WorldGenConnection connection(JsonObject json) {
        return new WorldGenConnection(string(json, "sourceNodeId", ""), string(json, "sourcePin", ""),
            string(json, "targetNodeId", ""), string(json, "targetPin", ""));
    }

    private static JsonObject settings(WorldGenProjectSettings value) {
        WorldGenProjectSettings settings = value == null ? new WorldGenProjectSettings() : value;
        JsonObject json = new JsonObject();
        put(json, "seedPolicy", settings.getSeedPolicy());
        put(json, "minY", settings.getMinY());
        put(json, "maxY", settings.getMaxY());
        put(json, "seaLevel", settings.getSeaLevel());
        put(json, "defaultBlock", settings.getDefaultBlock());
        put(json, "defaultFluid", settings.getDefaultFluid());
        put(json, "datapackNamespace", settings.getDatapackNamespace());
        put(json, "generatorBackend", settings.getGeneratorBackend());
        put(json, "generationMode", settings.getGenerationMode());
        put(json, "targetVersion", settings.getTargetVersion());
        put(json, "worldPreset", settings.getWorldPreset());
        put(json, "terrainTemplate", settings.getTerrainTemplate());
        put(json, "vanillaBiomesEnabled", settings.isVanillaBiomesEnabled());
        put(json, "vanillaFeaturesEnabled", settings.isVanillaFeaturesEnabled());
        put(json, "vanillaStructuresEnabled", settings.isVanillaStructuresEnabled());
        put(json, "vanillaSpawnsEnabled", settings.isVanillaSpawnsEnabled());
        put(json, "vanillaStructureTerrainSafety", settings.isVanillaStructureTerrainSafety());
        put(json, "vanillaStructureSampleRadius", settings.getVanillaStructureSampleRadius());
        put(json, "vanillaStructureMaxHeightDelta", settings.getVanillaStructureMaxHeightDelta());
        JsonObject overrides = new JsonObject();
        settings.getBiomeVanillaFeatureOverrides().forEach(overrides::addProperty);
        json.add("biomeVanillaFeatureOverrides", overrides);
        put(json, "previewEnvironment", settings.getPreviewEnvironment());
        put(json, "activePreviewPlayer", settings.getActivePreviewPlayer());
        return json;
    }

    private static WorldGenProjectSettings settings(JsonObject json) {
        WorldGenProjectSettings settings = new WorldGenProjectSettings();
        if (json == null) return settings;
        settings.setSeedPolicy(string(json, "seedPolicy", settings.getSeedPolicy()));
        settings.setMinY(integer(json, "minY", settings.getMinY()));
        settings.setMaxY(integer(json, "maxY", settings.getMaxY()));
        settings.setSeaLevel(integer(json, "seaLevel", settings.getSeaLevel()));
        settings.setDefaultBlock(string(json, "defaultBlock", settings.getDefaultBlock()));
        settings.setDefaultFluid(string(json, "defaultFluid", settings.getDefaultFluid()));
        settings.setDatapackNamespace(string(json, "datapackNamespace", settings.getDatapackNamespace()));
        settings.setGeneratorBackend(string(json, "generatorBackend", settings.getGeneratorBackend()));
        settings.setGenerationMode(string(json, "generationMode", settings.getGenerationMode()));
        settings.setTargetVersion(string(json, "targetVersion", settings.getTargetVersion()));
        settings.setWorldPreset(string(json, "worldPreset", settings.getWorldPreset()));
        settings.setTerrainTemplate(string(json, "terrainTemplate", settings.getTerrainTemplate()));
        settings.setVanillaBiomesEnabled(bool(json, "vanillaBiomesEnabled", settings.isVanillaBiomesEnabled()));
        settings.setVanillaFeaturesEnabled(bool(json, "vanillaFeaturesEnabled", settings.isVanillaFeaturesEnabled()));
        settings.setVanillaStructuresEnabled(bool(json, "vanillaStructuresEnabled", settings.isVanillaStructuresEnabled()));
        settings.setVanillaSpawnsEnabled(bool(json, "vanillaSpawnsEnabled", settings.isVanillaSpawnsEnabled()));
        settings.setVanillaStructureTerrainSafety(bool(json, "vanillaStructureTerrainSafety", settings.isVanillaStructureTerrainSafety()));
        settings.setVanillaStructureSampleRadius(integer(json, "vanillaStructureSampleRadius", settings.getVanillaStructureSampleRadius()));
        settings.setVanillaStructureMaxHeightDelta(integer(json, "vanillaStructureMaxHeightDelta", settings.getVanillaStructureMaxHeightDelta()));
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        JsonObject encodedOverrides = child(json, "biomeVanillaFeatureOverrides");
        if (encodedOverrides != null) encodedOverrides.entrySet().forEach(entry -> overrides.put(entry.getKey(), entry.getValue().getAsBoolean()));
        settings.setBiomeVanillaFeatureOverrides(overrides);
        settings.setPreviewEnvironment(string(json, "previewEnvironment", settings.getPreviewEnvironment()));
        settings.setActivePreviewPlayer(string(json, "activePreviewPlayer", settings.getActivePreviewPlayer()));
        return settings;
    }

    private static JsonObject profile(WorldGenBiomeProfile value) {
        JsonObject json = new JsonObject();
        put(json, "id", value.getId());
        put(json, "displayName", value.getDisplayName());
        put(json, "mode", value.getMode().name());
        put(json, "vanillaBaseBiome", value.getVanillaBaseBiome());
        put(json, "temperature", value.getTemperature());
        put(json, "humidity", value.getHumidity());
        put(json, "continentalness", value.getContinentalness());
        put(json, "erosion", value.getErosion());
        put(json, "weirdness", value.getWeirdness());
        put(json, "surfaceReference", value.getSurfaceReference());
        put(json, "keepVanillaFeatures", value.isKeepVanillaFeatures());
        put(json, "keepVanillaStructures", value.isKeepVanillaStructures());
        put(json, "keepVanillaSpawns", value.isKeepVanillaSpawns());
        JsonArray rules = new JsonArray();
        value.getSpawnRules().forEach(rule -> rules.add(rule(rule)));
        json.add("spawnRules", rules);
        return json;
    }

    private static WorldGenBiomeProfile profile(JsonObject json) {
        WorldGenBiomeProfile profile = new WorldGenBiomeProfile();
        profile.setId(string(json, "id", ""));
        profile.setDisplayName(string(json, "displayName", ""));
        try { profile.setMode(WorldGenBiomeProfileMode.valueOf(string(json, "mode", WorldGenBiomeProfileMode.CUSTOM.name()))); }
        catch (IllegalArgumentException ignored) { profile.setMode(WorldGenBiomeProfileMode.CUSTOM); }
        profile.setVanillaBaseBiome(string(json, "vanillaBaseBiome", "minecraft:plains"));
        profile.setTemperature((float) decimal(json, "temperature", 0.5));
        profile.setHumidity((float) decimal(json, "humidity", 0.5));
        profile.setContinentalness((float) decimal(json, "continentalness", 0.0));
        profile.setErosion((float) decimal(json, "erosion", 0.0));
        profile.setWeirdness((float) decimal(json, "weirdness", 0.0));
        profile.setSurfaceReference(string(json, "surfaceReference", ""));
        profile.setKeepVanillaFeatures(bool(json, "keepVanillaFeatures", false));
        profile.setKeepVanillaStructures(bool(json, "keepVanillaStructures", false));
        profile.setKeepVanillaSpawns(bool(json, "keepVanillaSpawns", false));
        List<WorldGenSpawnRule> rules = new ArrayList<>();
        array(json, "spawnRules").forEach(value -> { if (value.isJsonObject()) rules.add(rule(value.getAsJsonObject())); });
        profile.setSpawnRules(rules);
        return profile;
    }

    private static JsonObject rule(WorldGenSpawnRule value) {
        JsonObject json = new JsonObject();
        put(json, "entityType", value.getEntityType()); put(json, "weight", value.getWeight()); put(json, "minGroup", value.getMinGroup());
        put(json, "maxGroup", value.getMaxGroup()); put(json, "category", value.getCategory());
        JsonArray filters = new JsonArray(); value.getBiomeFilters().forEach(filters::add); json.add("biomeFilters", filters);
        put(json, "minY", value.getMinY()); put(json, "maxY", value.getMaxY()); put(json, "blockBelow", value.getBlockBelow());
        put(json, "minLight", value.getMinLight()); put(json, "maxLight", value.getMaxLight()); put(json, "time", value.getTime()); put(json, "weather", value.getWeather());
        return json;
    }

    private static WorldGenSpawnRule rule(JsonObject json) {
        WorldGenSpawnRule rule = new WorldGenSpawnRule();
        rule.setEntityType(string(json, "entityType", rule.getEntityType())); rule.setWeight(integer(json, "weight", rule.getWeight()));
        rule.setMinGroup(integer(json, "minGroup", rule.getMinGroup())); rule.setMaxGroup(integer(json, "maxGroup", rule.getMaxGroup()));
        rule.setCategory(string(json, "category", rule.getCategory()));
        List<String> filters = new ArrayList<>(); array(json, "biomeFilters").forEach(value -> filters.add(value.getAsString())); rule.setBiomeFilters(filters);
        rule.setMinY(integer(json, "minY", rule.getMinY())); rule.setMaxY(integer(json, "maxY", rule.getMaxY()));
        rule.setBlockBelow(string(json, "blockBelow", rule.getBlockBelow())); rule.setMinLight(integer(json, "minLight", rule.getMinLight()));
        rule.setMaxLight(integer(json, "maxLight", rule.getMaxLight())); rule.setTime(string(json, "time", rule.getTime()));
        rule.setWeather(string(json, "weather", rule.getWeather()));
        return rule;
    }

    private static JsonElement value(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement json) return json.deepCopy();
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Map<?, ?> map) {
            JsonObject json = new JsonObject();
            map.forEach((key, item) -> json.add(FlowJson.text(key), value(item)));
            return json;
        }
        if (value instanceof Iterable<?> items) {
            JsonArray json = new JsonArray(); items.forEach(item -> json.add(value(item))); return json;
        }
        return FlowJson.value(value);
    }

    private static Object value(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) { Map<String, Object> map = new LinkedHashMap<>(); value.getAsJsonObject().entrySet().forEach(entry -> map.put(entry.getKey(), value(entry.getValue()))); return map; }
        if (value.isJsonArray()) { List<Object> list = new ArrayList<>(); value.getAsJsonArray().forEach(item -> list.add(value(item))); return list; }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsDouble();
        return primitive.getAsString();
    }

    private static JsonObject object(String json) { JsonElement value = JsonTreeParser.parse(json); return value.isJsonObject() ? value.getAsJsonObject() : new JsonObject(); }
    private static JsonObject child(JsonObject json, String key) { JsonElement value = json == null ? null : json.get(key); return value != null && value.isJsonObject() ? value.getAsJsonObject() : null; }
    private static JsonArray array(JsonObject json, String key) { JsonElement value = json == null ? null : json.get(key); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static String string(JsonObject json, String key, String fallback) { try { JsonElement value = json == null ? null : json.get(key); return value != null && !value.isJsonNull() ? value.getAsString() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static int integer(JsonObject json, String key, int fallback) { try { JsonElement value = json == null ? null : json.get(key); return value != null ? value.getAsInt() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static double decimal(JsonObject json, String key, double fallback) { try { JsonElement value = json == null ? null : json.get(key); return value != null ? value.getAsDouble() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(JsonObject json, String key, boolean fallback) { try { JsonElement value = json == null ? null : json.get(key); return value != null ? value.getAsBoolean() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static void put(JsonObject json, String key, String value) { if (value == null) json.add(key, JsonNull.INSTANCE); else json.addProperty(key, value); }
    private static void put(JsonObject json, String key, Number value) { json.addProperty(key, value); }
    private static void put(JsonObject json, String key, Boolean value) { json.addProperty(key, value); }

    private WorldGenSerializer() {
    }
}
